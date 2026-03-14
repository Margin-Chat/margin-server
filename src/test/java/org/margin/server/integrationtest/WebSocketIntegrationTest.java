package org.margin.server.integrationtest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean private ConversationService conversationService;
    @MockitoBean private MessageService messageService;
    @MockitoBean private WebSocketDeliveryService webSocketDeliveryService;
    @Autowired private ConnectionManager connectionManager;

    private static final int WS_PORT = 8081;
    private static final String TEST_TOKEN = "test-token";

    private User testUser;
    private WebSocket webSocket;

    @BeforeEach
    void setUp() throws Exception {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        when(jwtService.extractAndValidateJwtTokenFromWebSocket(
                contains(TEST_TOKEN))).thenReturn(Optional.of(testUser));

        waitForPort(WS_PORT, 5000);
    }

    private void waitForPort(int port, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (var ignored = new java.net.Socket("localhost", port)) {
                return;
            } catch (Exception e) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Port " + port + " not available after " + timeoutMs + "ms");
    }

    @AfterEach
    void tearDown() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test done");
        }
    }

    @Test
    void connect_registersUserAsOnline() throws Exception {
        CompletableFuture<Void> connected = new CompletableFuture<>();

        webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + WS_PORT + "/ws?token=" + TEST_TOKEN),
                        new WebSocket.Listener() {
                            @Override
                            public void onOpen(WebSocket ws) {
                                connected.complete(null);
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        connected.get(5, TimeUnit.SECONDS);

        assertTrue(connectionManager.isUserOnline(testUser.getId()),
                "User should be registered as online after connecting");
    }

    @Test
    void sendMessage_triggersProcessorAndNotifiesRecipient() throws Exception {
        Conversation conversation = new Conversation();
        conversation.setId(10L);
        conversation.setType(ConversationType.DIRECT);

        User recipient = new User();
        recipient.setId(2L);
        recipient.setUsername("recipient");

        MessageDTO messageDTO = mock(MessageDTO.class);
        MessageResult result = new MessageResult(messageDTO, List.of(testUser, recipient));

        when(conversationService.getById(10L)).thenReturn(conversation);
        when(messageService.createMessage(any(), eq(conversation), eq("Hello integration")))
                .thenReturn(result);

        CompletableFuture<Void> connected = new CompletableFuture<>();

        webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + WS_PORT + "/ws?token=" + TEST_TOKEN),
                        new WebSocket.Listener() {
                            @Override
                            public void onOpen(WebSocket ws) {
                                connected.complete(null);
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        connected.get(5, TimeUnit.SECONDS);

        String frame = """
                {
                  "type": "SEND_MESSAGE",
                  "recipientId": 10,
                  "payload": "Hello integration"
                }
                """;

        webSocket.sendText(frame, true).get(5, TimeUnit.SECONDS);

        Thread.sleep(300);

        verify(messageService, timeout(2000))
                .createMessage(any(), eq(conversation), eq("Hello integration"));
        verify(webSocketDeliveryService, timeout(2000))
                .notifyMessage(eq(messageDTO), anyList(), eq(ConversationType.DIRECT));
    }

    @Test
    void disconnect_removesUserFromOnlineRegistry() throws Exception {
        CompletableFuture<Void> connected = new CompletableFuture<>();

        webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + WS_PORT + "/ws?token=" + TEST_TOKEN),
                        new WebSocket.Listener() {
                            @Override
                            public void onOpen(WebSocket ws) {
                                connected.complete(null);
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        connected.get(5, TimeUnit.SECONDS);
        assertTrue(connectionManager.isUserOnline(testUser.getId()));

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS);

        Thread.sleep(300);

        assertFalse(connectionManager.isUserOnline(testUser.getId()),
                "User should be offline after disconnecting");
    }

    @Test
    void invalidToken_rejectsConnection() {
        when(jwtService.extractAndValidateJwtTokenFromWebSocket(
                contains("bad-token"))).thenReturn(Optional.empty());

        assertThrows(Exception.class, () ->
                HttpClient.newHttpClient()
                        .newWebSocketBuilder()
                        .buildAsync(URI.create("ws://localhost:" + WS_PORT + "/ws?token=bad-token"),
                                new WebSocket.Listener() {})
                        .get(5, TimeUnit.SECONDS)
        );
    }
}