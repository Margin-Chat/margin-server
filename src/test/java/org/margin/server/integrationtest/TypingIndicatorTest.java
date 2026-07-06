package org.margin.server.integrationtest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.*;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.dtos.ConversationDTO;
import org.margin.server.users.models.User;

import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class TypingIndicatorTest extends MarginTestRunner {

    private WebSocket senderWs;
    private WebSocket receiverWs;
    private WebSocket otherWs;

    @AfterEach
    void tearDown() {
        WebSocketTestUtils.close(senderWs, receiverWs, otherWs);
    }

    @Test
    void typingIndicator_deliversToOtherDirectConversationMemberButNotSender() throws Exception {
        User sender = UserTestUtils.createUser("sender", "sender@margin.chat");
        User receiver = UserTestUtils.createUser("receiver", "receiver@margin.chat");
        Conversation conversation = ConversationTestUtils.createDirectConversation(sender, receiver);

        CompletableFuture<String> received = new CompletableFuture<>();
        CompletableFuture<String> senderEcho = new CompletableFuture<>();
        receiverWs = WebSocketTestUtils.connect(receiver,
                WebSocketTestUtils.listenerThatCompletes(received, "RECEIVE_TYPING_INDICATOR"));
        senderWs = WebSocketTestUtils.connect(sender,
                WebSocketTestUtils.listenerThatCompletes(senderEcho, "RECEIVE_TYPING_INDICATOR"));

        String frame = WebSocketFrameTestBuilder.ofType("SEND_TYPING_INDICATOR")
                .recipientId(conversation.getId())
                .payload(WebSocketFrameTestBuilder.object().put("isTyping", true))
                .build();

        senderWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        String receivedText = received.get(5, TimeUnit.SECONDS);
        assertTrue(receivedText.contains("\"isTyping\":true"));
        assertTrue(receivedText.contains(String.valueOf(sender.getId())));

        assertThrows(TimeoutException.class, () -> senderEcho.get(1, TimeUnit.SECONDS));
    }

    @Test
    void typingIndicator_deliversToAllGroupMembersExceptSender() throws Exception {
        User creator = UserTestUtils.createUser("creator", "creator@margin.chat");
        User memberA = UserTestUtils.createUser("memberA", "memberA@margin.chat");
        User memberB = UserTestUtils.createUser("memberB", "memberB@margin.chat");

        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat", "memberB@margin.chat"), "Typing Group", false);
        ConversationTestUtils.acceptInvite(dto.id(), memberA);
        ConversationTestUtils.acceptInvite(dto.id(), memberB);

        CompletableFuture<String> memberAReceived = new CompletableFuture<>();
        CompletableFuture<String> memberBReceived = new CompletableFuture<>();
        CompletableFuture<String> creatorEcho = new CompletableFuture<>();

        receiverWs = WebSocketTestUtils.connect(memberA,
                WebSocketTestUtils.listenerThatCompletes(memberAReceived, "RECEIVE_TYPING_INDICATOR"));
        otherWs = WebSocketTestUtils.connect(memberB,
                WebSocketTestUtils.listenerThatCompletes(memberBReceived, "RECEIVE_TYPING_INDICATOR"));
        senderWs = WebSocketTestUtils.connect(creator,
                WebSocketTestUtils.listenerThatCompletes(creatorEcho, "RECEIVE_TYPING_INDICATOR"));

        String frame = WebSocketFrameTestBuilder.ofType("SEND_TYPING_INDICATOR")
                .recipientId(dto.id())
                .payload(WebSocketFrameTestBuilder.object().put("isTyping", true))
                .build();

        senderWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        assertTrue(memberAReceived.get(5, TimeUnit.SECONDS).contains(String.valueOf(creator.getId())));
        assertTrue(memberBReceived.get(5, TimeUnit.SECONDS).contains(String.valueOf(creator.getId())));
        assertThrows(TimeoutException.class, () -> creatorEcho.get(1, TimeUnit.SECONDS));
    }

    @Test
    void typingIndicator_userNotInConversation_isRejectedAndDeliversNothing() throws Exception {
        User sender = UserTestUtils.createUser("sender", "sender@margin.chat");
        User receiver = UserTestUtils.createUser("receiver", "receiver@margin.chat");
        User outsider = UserTestUtils.createUser("outsider", "outsider@margin.chat");
        Conversation conversation = ConversationTestUtils.createDirectConversation(sender, receiver);

        CompletableFuture<String> received = new CompletableFuture<>();
        receiverWs = WebSocketTestUtils.connect(receiver,
                WebSocketTestUtils.listenerThatCompletes(received, "RECEIVE_TYPING_INDICATOR"));
        senderWs = WebSocketTestUtils.connect(outsider);

        String frame = WebSocketFrameTestBuilder.ofType("SEND_TYPING_INDICATOR")
                .recipientId(conversation.getId())
                .payload(WebSocketFrameTestBuilder.object().put("isTyping", true))
                .build();

        senderWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        assertThrows(TimeoutException.class, () -> received.get(1, TimeUnit.SECONDS));
    }

    @Test
    void typingIndicator_isTypingFalse_isForwardedAsIs() throws Exception {
        User sender = UserTestUtils.createUser("sender", "sender@margin.chat");
        User receiver = UserTestUtils.createUser("receiver", "receiver@margin.chat");
        Conversation conversation = ConversationTestUtils.createDirectConversation(sender, receiver);

        CompletableFuture<String> received = new CompletableFuture<>();
        receiverWs = WebSocketTestUtils.connect(receiver,
                WebSocketTestUtils.listenerThatCompletes(received, "RECEIVE_TYPING_INDICATOR"));
        senderWs = WebSocketTestUtils.connect(sender);

        String frame = WebSocketFrameTestBuilder.ofType("SEND_TYPING_INDICATOR")
                .recipientId(conversation.getId())
                .payload(WebSocketFrameTestBuilder.object().put("isTyping", false))
                .build();

        senderWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        String receivedText = received.get(5, TimeUnit.SECONDS);
        assertTrue(receivedText.contains("\"isTyping\":false"));
    }
}
