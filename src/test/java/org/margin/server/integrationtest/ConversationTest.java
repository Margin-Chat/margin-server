package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.*;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.conversation.models.dtos.GetConversationMessagesResponse;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.users.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConversationTest extends MarginTestRunner {

    private User admin;
    private User member;
    private User outsider;
    private ChannelDTO channel;

    @BeforeEach
    void setUp() {
        admin = UserTestUtils.createUser("admin", "admin@margin.chat");
        member = UserTestUtils.createUser("member", "member@margin.chat");
        outsider = UserTestUtils.createUser("outsider", "outsider@margin.chat");

        Margin margin = MarginTestUtils.createMargin("TestMargin", admin);
        MarginTestUtils.addUserToMargin(margin.getId(), admin, member);

        SpaceDTO space = SpaceTestUtils.createSpace("Test Space", margin.getId(), admin);
        SpaceTestUtils.addMember(space.spaceId(), member, SpaceRole.MEMBER, admin);

        channel = ChannelTestUtils.createChannel(space.spaceId(), "Test Channel", admin);
    }

    @Test
    void spaceMemberCanGetChannelMessages() {
        GetConversationMessagesResponse response =
                ConversationTestUtils.getChannelMessages(channel.id(), member);

        assertNotNull(response);
        assertNotNull(response.messages());
    }

    @Test
    void channelCreatorCanGetChannelMessages() {
        GetConversationMessagesResponse response =
                ConversationTestUtils.getChannelMessages(channel.id(), admin);

        assertNotNull(response);
        assertNotNull(response.messages());
    }

    @Test
    void nonSpaceMemberCannotGetChannelMessages() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ConversationTestUtils.getChannelMessages(channel.id(), outsider));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void marginMemberNotInSpaceCannotGetChannelMessages() {
        User marginMember = UserTestUtils.createUser("marginonly", "marginonly@margin.chat");
        Margin margin = MarginTestUtils.createMargin("AnotherMargin", admin);
        SpaceDTO space = SpaceTestUtils.createSpace("Private Space", margin.getId(), admin);
        ChannelDTO privateChannel = ChannelTestUtils.createChannel(space.spaceId(), "Private Channel", admin);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ConversationTestUtils.getChannelMessages(privateChannel.id(), marginMember));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
