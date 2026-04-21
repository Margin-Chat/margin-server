package org.margin.server.integrationtest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.ConversationTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.integrationtest.utils.WebSocketFrameTestBuilder;
import org.margin.server.integrationtest.utils.WebSocketTestUtils;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.users.models.User;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiSessionTest extends MarginTestRunner {

    private WebSocket deviceOneWs;
    private WebSocket deviceTwoWs;
    private WebSocket otherWs;

    @AfterEach
    void tearDown() {
        WebSocketTestUtils.close(deviceOneWs, deviceTwoWs, otherWs);
    }

    @Test
    void multiSession_bothDevicesReceiveMessages() throws Exception {
        User user = UserTestUtils.createUser("user", "user@margin.chat");
        User sender = UserTestUtils.createUser("sender", "sender@margin.chat");
        Conversation conversation = ConversationTestUtils.createDirectConversation(user, sender);

        CompletableFuture<String> deviceOneReceived = new CompletableFuture<>();
        CompletableFuture<String> deviceTwoReceived = new CompletableFuture<>();

        deviceOneWs = WebSocketTestUtils.connect(user,
                WebSocketTestUtils.listenerThatCompletes(deviceOneReceived, "RECEIVE_MESSAGE"));
        deviceTwoWs = WebSocketTestUtils.connect(user,
                WebSocketTestUtils.listenerThatCompletes(deviceTwoReceived, "RECEIVE_MESSAGE"));
        otherWs = WebSocketTestUtils.connect(sender);

        String frame = WebSocketFrameTestBuilder.ofType("SEND_MESSAGE")
                .recipientId(conversation.getId())
                .payload("Hello both devices")
                .build();

        otherWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        String deviceOneText = deviceOneReceived.get(5, TimeUnit.SECONDS);
        String deviceTwoText = deviceTwoReceived.get(5, TimeUnit.SECONDS);

        assertTrue(deviceOneText.contains("Hello both devices"));
        assertTrue(deviceTwoText.contains("Hello both devices"));
    }

    @Test
    void multiSession_connectingSecondDeviceDoesNotDisconnectFirst() throws Exception {
        User user = UserTestUtils.createUser("user", "user@margin.chat");
        User sender = UserTestUtils.createUser("sender", "sender@margin.chat");
        Conversation conversation = ConversationTestUtils.createDirectConversation(user, sender);

        CompletableFuture<String> deviceOneReceived = new CompletableFuture<>();

        deviceOneWs = WebSocketTestUtils.connect(user,
                WebSocketTestUtils.listenerThatCompletes(deviceOneReceived, "RECEIVE_MESSAGE"));

        deviceTwoWs = WebSocketTestUtils.connect(user);
        otherWs = WebSocketTestUtils.connect(sender);

        String frame = WebSocketFrameTestBuilder.ofType("SEND_MESSAGE")
                .recipientId(conversation.getId())
                .payload("Still alive")
                .build();

        otherWs.sendText(frame, true).get(5, TimeUnit.SECONDS);

        String text = deviceOneReceived.get(5, TimeUnit.SECONDS);
        assertTrue(text.contains("Still alive"));
    }

    @Test
    void multiSession_userStaysOnlineUntilLastSessionDisconnects() throws Exception {
        User user = UserTestUtils.createUser("user", "user@margin.chat");
        User observer = UserTestUtils.createUser("observer", "observer@margin.chat");

        CompletableFuture<String> logoutReceived = new CompletableFuture<>();
        otherWs = WebSocketTestUtils.connect(observer,
                WebSocketTestUtils.listenerThatCompletes(logoutReceived, "USER_LOGOUT"));

        deviceOneWs = WebSocketTestUtils.connect(user);
        deviceTwoWs = WebSocketTestUtils.connect(user);

        WebSocketTestUtils.close(deviceOneWs);
        deviceOneWs = null;

        assertThrows(TimeoutException.class,
                () -> logoutReceived.get(1, TimeUnit.SECONDS));

        WebSocketTestUtils.close(deviceTwoWs);
        deviceTwoWs = null;

        String logoutText = logoutReceived.get(5, TimeUnit.SECONDS);
        assertTrue(logoutText.contains("USER_LOGOUT"));
    }
}
