package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.*;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.dtos.UnreadConversationsDTO;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.users.models.User;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UnreadConversationsTest extends MarginTestRunner {

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = UserTestUtils.createUser("alice", "alice@margin.chat");
        bob = UserTestUtils.createUser("bob", "bob@margin.chat");
    }

    @Test
    void directConversationWithUnreadMessage_appearsInUnreadList() {
        Conversation conv = ConversationTestUtils.createDirectConversation(alice, bob);
        MessageTestUtils.saveMessage(conv, bob, "hey alice", false);

        UnreadConversationsDTO unread = ConversationTestUtils.getUnreadConversations(alice);

        assertTrue(unread.directConversationIds().contains(conv.getId()),
                "Direct conversation with unread message should appear in directConversationIds");
        assertTrue(unread.channelUnreads().isEmpty());
    }

    @Test
    void directConversationWithNoMessages_notInUnreadList() {
        ConversationTestUtils.createDirectConversation(alice, bob);

        UnreadConversationsDTO unread = ConversationTestUtils.getUnreadConversations(alice);

        assertTrue(unread.directConversationIds().isEmpty());
    }

    @Test
    void directConversationWithOnlyOwnMessages_notInUnreadList() {
        Conversation conv = ConversationTestUtils.createDirectConversation(alice, bob);
        MessageTestUtils.saveMessage(conv, alice, "hello", false);

        UnreadConversationsDTO unread = ConversationTestUtils.getUnreadConversations(alice);

        assertFalse(unread.directConversationIds().contains(conv.getId()));
    }

    @Test
    void multipleDirectConversationsWithUnreads_allAppearInList() {
        User charlie = UserTestUtils.createUser("charlie", "charlie@margin.chat");
        Conversation conv1 = ConversationTestUtils.createDirectConversation(alice, bob);
        Conversation conv2 = ConversationTestUtils.createDirectConversation(alice, charlie);
        MessageTestUtils.saveMessage(conv1, bob, "hi", false);
        MessageTestUtils.saveMessage(conv2, charlie, "hey", false);

        UnreadConversationsDTO unread = ConversationTestUtils.getUnreadConversations(alice);

        assertTrue(unread.directConversationIds().contains(conv1.getId()));
        assertTrue(unread.directConversationIds().contains(conv2.getId()));
    }

    @Test
    void channelConversationWithUnreadMessage_appearsInChannelUnreads() {
        User admin = UserTestUtils.createUser("admin", "admin@margin.chat");
        Margin margin = MarginTestUtils.createMargin("TestMargin", admin);
        MarginTestUtils.addUserToMargin(margin.getId(), admin, alice);
        SpaceDTO space = SpaceTestUtils.createSpace("Test Space", margin.getId(), admin);
        ChannelDTO channel = ChannelTestUtils.createChannel(space.spaceId(), "general", admin);

        Conversation channelConv = ConversationTestUtils.getConversationById(channel.conversationId());
        MessageTestUtils.saveMessage(channelConv, admin, "welcome!", false);

        UnreadConversationsDTO unread = ConversationTestUtils.getUnreadConversations(alice);

        assertTrue(unread.channelUnreads().stream()
                        .anyMatch(u -> u.conversationId().equals(channel.conversationId())),
                "Channel conversation with unread message should appear in channelUnreads");
    }

    @Test
    void directAndChannelUnreadsCoexist_bothReturnedTogether() {
        Conversation directConv = ConversationTestUtils.createDirectConversation(alice, bob);
        MessageTestUtils.saveMessage(directConv, bob, "hi", false);

        User admin = UserTestUtils.createUser("admin2", "admin2@margin.chat");
        Margin margin = MarginTestUtils.createMargin("TestMargin2", admin);
        MarginTestUtils.addUserToMargin(margin.getId(), admin, alice);
        SpaceDTO space = SpaceTestUtils.createSpace("Test Space2", margin.getId(), admin);
        ChannelDTO channel = ChannelTestUtils.createChannel(space.spaceId(), "general", admin);
        Conversation channelConv = ConversationTestUtils.getConversationById(channel.conversationId());
        MessageTestUtils.saveMessage(channelConv, admin, "welcome!", false);

        UnreadConversationsDTO unread = ConversationTestUtils.getUnreadConversations(alice);

        assertTrue(unread.directConversationIds().contains(directConv.getId()),
                "Direct conversation should not be dropped when channel unreads also exist");
        assertTrue(unread.channelUnreads().stream()
                .anyMatch(u -> u.conversationId().equals(channel.conversationId())));
    }
}
