package org.margin.server.integrationtest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.*;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.messages.models.Message;
import org.margin.server.users.models.User;

import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SendMessageTest extends MarginTestRunner {

    private WebSocket senderWs;
    private WebSocket receiverWs;

    @AfterEach
    void tearDown() {
        WebSocketTestUtils.close(senderWs, receiverWs);
    }

    @Test
    void sendMessage_persistsAndDeliversToRecipient() throws Exception {
        User sender = UserTestUtils.createUser("sender", "sender@margin.chat");
        User receiver = UserTestUtils.createUser("receiver", "receiver@margin.chat");
        Conversation conversation = ConversationTestUtils.createDirectConversation(sender, receiver);

        CompletableFuture<String> received = new CompletableFuture<>();
        receiverWs = WebSocketTestUtils.connect(receiver,
                WebSocketTestUtils.listenerThatCompletes(received, "RECEIVE_MESSAGE"));
        senderWs = WebSocketTestUtils.connect(sender);

        String frame = WebSocketFrameTestBuilder.ofType("SEND_MESSAGE")
                .recipientId(conversation.getId())
                .payload(WebSocketFrameTestBuilder.object().put("content", "Hello from integration test"))
                .build();

        senderWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        String receivedText = received.get(5, TimeUnit.SECONDS);
        assertTrue(receivedText.contains("Hello from integration test"));

        Thread.sleep(500);

        List<Message> messages = MessageTestUtils.getMessages(conversation.getId());
        assertEquals(1, messages.size());
        assertEquals("Hello from integration test", messages.getFirst().getMessage());
    }

    @Test
    void sendMessage_senderReceivesEcho() throws Exception {
        User sender = UserTestUtils.createUser("sender", "sender@margin.chat");
        User receiver = UserTestUtils.createUser("receiver", "receiver@margin.chat");
        Conversation conversation = ConversationTestUtils.createDirectConversation(sender, receiver);

        CompletableFuture<String> echo = new CompletableFuture<>();
        senderWs = WebSocketTestUtils.connect(sender,
                WebSocketTestUtils.listenerThatCompletes(echo, "RECEIVE_MESSAGE"));

        String frame = WebSocketFrameTestBuilder.ofType("SEND_MESSAGE")
                .recipientId(conversation.getId())
                .payload(WebSocketFrameTestBuilder.object().put("content", "Echo test"))
                .build();

        senderWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        String echoText = echo.get(5, TimeUnit.SECONDS);
        assertTrue(echoText.contains("Echo test"));
    }

    @Test
    void sendMessage_toNonExistentConversation_doesNotPersist() throws Exception {
        User sender = UserTestUtils.createUser("sender", "sender@margin.chat");

        senderWs = WebSocketTestUtils.connect(sender);

        String frame = WebSocketFrameTestBuilder.ofType("SEND_MESSAGE")
                .recipientId(99999)
                .payload(WebSocketFrameTestBuilder.object().put("content", "Should not persist"))
                .build();

        senderWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        List<Message> messages = MessageTestUtils.getMessages(99999L);
        assertTrue(messages.isEmpty());
    }
}