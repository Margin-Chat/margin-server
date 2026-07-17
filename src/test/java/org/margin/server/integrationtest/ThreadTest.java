package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.*;
import org.margin.server.notifications.Notification;
import org.margin.server.notifications.NotificationType;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.conversation.models.dtos.ThreadConversationDTO;
import org.margin.server.social.conversation.models.dtos.ThreadSummaryDTO;
import org.margin.server.social.conversation.models.dtos.UnreadConversationsDTO;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.users.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ThreadTest extends MarginTestRunner {

    private User alice;
    private User bob;
    private User carol;
    private ChannelDTO threadChannel;
    private ChannelDTO chatChannel;

    @BeforeEach
    void setUp() {
        alice = UserTestUtils.createUser("alice", "alice@margin.chat");
        bob = UserTestUtils.createUser("bob", "bob@margin.chat");
        carol = UserTestUtils.createUser("carol", "carol@margin.chat");

        Margin margin = MarginTestUtils.createMargin("ThreadMargin", alice);
        MarginTestUtils.addUserToMargin(margin.getId(), alice, bob);
        MarginTestUtils.addUserToMargin(margin.getId(), alice, carol);
        SpaceDTO space = SpaceTestUtils.createSpace("Thread Space", margin.getId(), alice);
        threadChannel = ChannelTestUtils.createThreadChannel(space.spaceId(), "forum", alice);
        chatChannel = ChannelTestUtils.createChannel(space.spaceId(), "general", alice);
    }

    @Test
    void createPost_returnsThreadAnchoredToChannel() {
        ThreadConversationDTO post = ThreadTestUtils.createPost(alice, threadChannel.id(),
                "First post", "hello forum");

        assertEquals("First post", post.title());
        assertEquals(threadChannel.conversationId(), post.parentConversationId());
        assertEquals(threadChannel.id(), post.channelId());
        assertNotNull(post.marginId());
        assertTrue(post.following(), "The post author should follow their post");
    }

    @Test
    void createPost_inChatChannel_isRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ThreadTestUtils.createPost(alice, chatChannel.id(), "nope", "body"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createPost_withBlankTitleOrBody_isRejected() {
        ResponseStatusException noTitle = assertThrows(ResponseStatusException.class,
                () -> ThreadTestUtils.createPost(alice, threadChannel.id(), "  ", "body"));
        assertEquals(HttpStatus.BAD_REQUEST, noTitle.getStatusCode());

        ResponseStatusException noBody = assertThrows(ResponseStatusException.class,
                () -> ThreadTestUtils.createPost(alice, threadChannel.id(), "title", " "));
        assertEquals(HttpStatus.BAD_REQUEST, noBody.getStatusCode());
    }

    @Test
    void createPost_byNonMember_isForbidden() {
        User mallory = UserTestUtils.createUser("mallory", "mallory@margin.chat");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ThreadTestUtils.createPost(mallory, threadChannel.id(), "hi", "body"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void sendingDirectlyToThreadChannelConversation_isRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ThreadTestUtils.sendMessage(alice, threadChannel.conversationId(), "raw message"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void postList_showsAuthorBodyAndReplyCounts() {
        ThreadConversationDTO post = ThreadTestUtils.createPost(alice, threadChannel.id(),
                "Release plan", "the plan is simple");
        ThreadTestUtils.sendMessage(bob, post.id(), "reply one");
        ThreadTestUtils.sendMessage(carol, post.id(), "reply two");

        List<ThreadSummaryDTO> posts = ThreadTestUtils.getPostsForChannel(bob, threadChannel.id());

        assertEquals(1, posts.size());
        ThreadSummaryDTO summary = posts.get(0);
        assertEquals("Release plan", summary.thread().title());
        assertEquals(alice.getId(), summary.author().id());
        assertEquals("the plan is simple", summary.bodyExcerpt());
        assertEquals(2, summary.replyCount());
        assertNotNull(summary.lastReplyAt());
    }

    @Test
    void postList_byNonMember_isForbidden() {
        User mallory = UserTestUtils.createUser("mallory2", "mallory2@margin.chat");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ThreadTestUtils.getPostsForChannel(mallory, threadChannel.id()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getThread_byNonMember_isForbidden() {
        ThreadConversationDTO post = ThreadTestUtils.createPost(alice, threadChannel.id(), "p", "b");
        User mallory = UserTestUtils.createUser("mallory3", "mallory3@margin.chat");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ThreadTestUtils.getThread(mallory, post.id()));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void reply_notifiesAuthorAndPriorRepliers() {
        ThreadConversationDTO post = ThreadTestUtils.createPost(alice, threadChannel.id(), "p", "body");

        ThreadTestUtils.sendMessage(bob, post.id(), "first reply");
        assertTrue(NotificationTestUtils.hasNotification(alice, NotificationType.THREAD_REPLY),
                "The post author should be notified of a reply");
        assertFalse(NotificationTestUtils.hasNotification(bob, NotificationType.THREAD_REPLY),
                "The replier must not be notified of their own reply");

        ThreadTestUtils.sendMessage(carol, post.id(), "second reply");
        assertTrue(NotificationTestUtils.hasNotification(bob, NotificationType.THREAD_REPLY),
                "Prior repliers should be notified of later replies");
        assertFalse(NotificationTestUtils.hasNotification(carol, NotificationType.THREAD_REPLY));
    }

    @Test
    void replies_collapseIntoOneUnseenNotification() {
        ThreadConversationDTO post = ThreadTestUtils.createPost(alice, threadChannel.id(), "p", "body");

        ThreadTestUtils.sendMessage(bob, post.id(), "reply one");
        ThreadTestUtils.sendMessage(bob, post.id(), "reply two");

        List<Notification> threadNotifications = NotificationTestUtils.getForUser(alice).stream()
                .filter(n -> n.getType() == NotificationType.THREAD_REPLY)
                .toList();
        assertEquals(1, threadNotifications.size(),
                "Unseen thread reply notifications for the same post should collapse");
        assertEquals(post.id(), threadNotifications.get(0).getConversationId());
    }

    @Test
    void posts_doNotAppearInConversationList() {
        ThreadConversationDTO post = ThreadTestUtils.createPost(alice, threadChannel.id(), "p", "body");

        assertTrue(ConversationTestUtils.getUserConversations(alice).stream()
                        .noneMatch(c -> c.id().equals(post.id())),
                "Posts must not appear in the conversation list");
    }

    @Test
    void postUnread_tracksForFollowersAndClearsOnRead() {
        ThreadConversationDTO post = ThreadTestUtils.createPost(alice, threadChannel.id(), "p", "body");

        ThreadTestUtils.sendMessage(bob, post.id(), "reply");

        UnreadConversationsDTO unread = ConversationTestUtils.getUnreadConversations(alice);
        assertTrue(unread.threadConversationIds().contains(post.id()),
                "A followed post with a reply from someone else should be unread");

        ConversationTestUtils.markConversationAsRead(post.id(), alice);
        unread = ConversationTestUtils.getUnreadConversations(alice);
        assertFalse(unread.threadConversationIds().contains(post.id()));

        UnreadConversationsDTO carolUnread = ConversationTestUtils.getUnreadConversations(carol);
        assertFalse(carolUnread.threadConversationIds().contains(post.id()),
                "Non-followers should not see post unreads");
    }

    @Test
    void postMessages_carryParentConversationPointer() {
        ThreadConversationDTO post = ThreadTestUtils.createPost(alice, threadChannel.id(), "p", "the body");
        ThreadTestUtils.sendMessage(bob, post.id(), "a reply");

        List<MessageDTO> messages = ConversationTestUtils.getGroupMessages(post.id(), bob).messages();
        assertEquals(2, messages.size(), "Body + one reply");
        assertEquals("the body", messages.get(0).content());
        assertEquals(threadChannel.conversationId(), messages.get(0).parentConversationId());
        assertNotNull(messages.get(0).marginId(), "Post messages should carry margin context");
    }

    @Test
    void followedThreads_returnsInboxWithUnreadState() {
        ThreadConversationDTO post = ThreadTestUtils.createPost(alice, threadChannel.id(),
                "Inbox post", "the body");
        ThreadTestUtils.sendMessage(bob, post.id(), "a reply");

        List<ThreadSummaryDTO> aliceInbox = ThreadTestUtils.getFollowedThreads(alice);
        assertEquals(1, aliceInbox.size());
        ThreadSummaryDTO summary = aliceInbox.get(0);
        assertEquals(post.id(), summary.thread().id());
        assertEquals("Inbox post", summary.thread().title());
        assertEquals(alice.getId(), summary.author().id());
        assertEquals(1, summary.replyCount());
        assertTrue(summary.unread());

        List<ThreadSummaryDTO> bobInbox = ThreadTestUtils.getFollowedThreads(bob);
        assertEquals(1, bobInbox.size(), "Replying should auto-follow");
        assertFalse(bobInbox.get(0).unread(), "Own replies must not mark the post unread");
    }

    @Test
    void unfollow_stopsNotificationsAndInboxListing() {
        ThreadConversationDTO post = ThreadTestUtils.createPost(alice, threadChannel.id(), "p", "body");

        ThreadTestUtils.unfollow(alice, post.id());
        ThreadTestUtils.sendMessage(bob, post.id(), "reply");

        assertFalse(NotificationTestUtils.hasNotification(alice, NotificationType.THREAD_REPLY),
                "Unfollowed users must not be notified");
        assertTrue(ThreadTestUtils.getFollowedThreads(alice).isEmpty());
    }

    @Test
    void explicitFollow_subscribesToNotifications() {
        ThreadConversationDTO post = ThreadTestUtils.createPost(alice, threadChannel.id(), "p", "body");

        ThreadTestUtils.follow(carol, post.id());
        ThreadTestUtils.sendMessage(bob, post.id(), "reply");

        assertTrue(NotificationTestUtils.hasNotification(carol, NotificationType.THREAD_REPLY),
                "Explicit followers should be notified without having replied");
    }
}
