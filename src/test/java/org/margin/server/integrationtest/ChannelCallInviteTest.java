package org.margin.server.integrationtest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.*;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.users.models.User;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelCallInviteTest extends MarginTestRunner {

    private User inviter;
    private User invitee;
    private ChannelDTO channel;
    private SpaceDTO space;
    private Margin margin;

    private WebSocket inviterWs;
    private WebSocket inviteeWs;

    @BeforeEach
    void setUp() {
        inviter = UserTestUtils.createUser("inviter", "inviter@margin.chat");
        invitee = UserTestUtils.createUser("invitee", "invitee@margin.chat");
        margin = MarginTestUtils.createMargin("CallInviteMargin", inviter);
        MarginTestUtils.addUserToMargin(margin.getId(), inviter, invitee);
        // Private space so margin members aren't auto-added; channel access stays explicit
        space = SpaceTestUtils.createPrivateSpace("Call Space", margin.getId(), inviter);
        SpaceTestUtils.addMember(space.spaceId(), invitee, SpaceRole.MEMBER, inviter);
        channel = ChannelTestUtils.createChannel(space.spaceId(), "Voice Channel", inviter);
    }

    @AfterEach
    void tearDown() {
        WebSocketTestUtils.close(inviterWs, inviteeWs);
    }

    @Test
    void channelCallInvite_deliversChannelAndInviterToRecipient() throws Exception {
        CompletableFuture<String> received = new CompletableFuture<>();
        inviteeWs = WebSocketTestUtils.connect(invitee,
                WebSocketTestUtils.listenerThatCompletes(received, "CHANNEL_CALL_INVITE"));
        inviterWs = WebSocketTestUtils.connect(inviter);

        String frame = WebSocketFrameTestBuilder.ofType("CHANNEL_CALL_INVITE")
                .recipientId(invitee.getId())
                .payload(String.valueOf(channel.id()))
                .build();

        inviterWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        String receivedText = received.get(5, TimeUnit.SECONDS);
        assertTrue(receivedText.contains("\"channelId\":" + channel.id()));
        assertTrue(receivedText.contains("Voice Channel"));
        assertTrue(receivedText.contains(String.valueOf(inviter.getId())));
    }

    @Test
    void channelCallInvite_inviterWithoutChannelAccess_deliversNothing() throws Exception {
        User outsider = UserTestUtils.createUser("outsider", "outsider@margin.chat");
        MarginTestUtils.addUserToMargin(margin.getId(), inviter, outsider);

        CompletableFuture<String> received = new CompletableFuture<>();
        inviteeWs = WebSocketTestUtils.connect(invitee,
                WebSocketTestUtils.listenerThatCompletes(received, "CHANNEL_CALL_INVITE"));
        inviterWs = WebSocketTestUtils.connect(outsider);

        String frame = WebSocketFrameTestBuilder.ofType("CHANNEL_CALL_INVITE")
                .recipientId(invitee.getId())
                .payload(String.valueOf(channel.id()))
                .build();

        inviterWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        assertThrows(TimeoutException.class, () -> received.get(1, TimeUnit.SECONDS));
    }

    @Test
    void channelCallInvite_recipientWithoutChannelAccess_deliversNothing() throws Exception {
        User stranger = UserTestUtils.createUser("stranger", "stranger@margin.chat");
        MarginTestUtils.addUserToMargin(margin.getId(), inviter, stranger);

        CompletableFuture<String> received = new CompletableFuture<>();
        inviteeWs = WebSocketTestUtils.connect(stranger,
                WebSocketTestUtils.listenerThatCompletes(received, "CHANNEL_CALL_INVITE"));
        inviterWs = WebSocketTestUtils.connect(inviter);

        String frame = WebSocketFrameTestBuilder.ofType("CHANNEL_CALL_INVITE")
                .recipientId(stranger.getId())
                .payload(String.valueOf(channel.id()))
                .build();

        inviterWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        assertThrows(TimeoutException.class, () -> received.get(1, TimeUnit.SECONDS));
    }
}
