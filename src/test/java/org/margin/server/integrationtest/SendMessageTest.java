package org.margin.server.integrationtest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.margin.server.integrationtest.IntegrationTestHelper.*;

class SendMessageIntegrationTest extends MarginTestRunner {
    @Autowired private MessageRepository messageRepository;
    @Autowired private JwtService jwtService;

    private WebSocket senderWs;
    private WebSocket receiverWs;

    @AfterEach
    void tearDown() {
        closeWebSocket(senderWs, receiverWs);
    }

    @Test
    void sendMessage_persistsAndDeliversToRecipient() throws Exception {
        User sender = IntegrationTestHelper.createUser("sender", "sender@margin.chat");
        User receiver = IntegrationTestHelper.createUser("receiver", "receiver@margin.chat");
        Conversation conversation = IntegrationTestHelper.createDirectConversation(sender, receiver);

        CompletableFuture<String> received = new CompletableFuture<>();
        receiverWs = connectWebSocket(WS_PORT, jwtService.generateToken(receiver.getEmail(), receiver.getId()),
                listenerThatCompletes(received, "RECEIVE_MESSAGE"));
        senderWs = connectWebSocket(WS_PORT, jwtService.generateToken(sender.getEmail(), sender.getId()),
                new WebSocket.Listener() {});

        senderWs.sendText(wsFrame("SEND_MESSAGE", conversation.getId(), "Hello from integration test"), true)
                .get(5, TimeUnit.SECONDS);

        String receivedText = received.get(5, TimeUnit.SECONDS);
        assertTrue(receivedText.contains("Hello from integration test"));

        Thread.sleep(500);

        List<Message> messages = messageRepository.findRecentMessages(conversation.getId(), Pageable.ofSize(10));
        assertEquals(1, messages.size());
        assertEquals("Hello from integration test", messages.getFirst().getMessage());
    }

    @Test
    void sendMessage_senderReceivesEcho() throws Exception {
        User sender = IntegrationTestHelper.createUser("sender", "sender@margin.chat");
        User receiver = IntegrationTestHelper.createUser("receiver", "receiver@margin.chat");
        Conversation conversation = IntegrationTestHelper.createDirectConversation(sender, receiver);

        CompletableFuture<String> echo = new CompletableFuture<>();
        senderWs = connectWebSocket(WS_PORT, jwtService.generateToken(sender.getEmail(), sender.getId()),
                listenerThatCompletes(echo, "RECEIVE_MESSAGE"));

        senderWs.sendText(wsFrame("SEND_MESSAGE", conversation.getId(), "Echo test"), true)
                .get(5, TimeUnit.SECONDS);

        String echoText = echo.get(5, TimeUnit.SECONDS);
        assertTrue(echoText.contains("Echo test"));
    }

    @Test
    void sendMessage_toNonExistentConversation_doesNotPersist() throws Exception {
        User sender = IntegrationTestHelper.createUser("sender", "sender@margin.chat");

        senderWs = connectWebSocket(WS_PORT, jwtService.generateToken(sender.getEmail(), sender.getId()),
                new WebSocket.Listener() {});

        senderWs.sendText(wsFrame("SEND_MESSAGE", 99999, "Should not persist"), true)
                .get(5, TimeUnit.SECONDS);

        Thread.sleep(500);

        List<Message> messages = messageRepository.findRecentMessages(99999L, Pageable.ofSize(10));
        assertTrue(messages.isEmpty());
    }
}