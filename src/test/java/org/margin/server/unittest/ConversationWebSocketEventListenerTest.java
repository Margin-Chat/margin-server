package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.conversation.events.TypingIndicatorEvent;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.listeners.ConversationWebSocketEventListener;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationWebSocketEventListenerTest {

    @Mock
    private WebSocketMessageBuilder messageBuilder;
    @Mock
    private ConnectionManager connectionManager;
    @Mock
    private UserService userService;

    @InjectMocks
    private ConversationWebSocketEventListener listener;

    @Test
    void onTypingIndicator_sendsOnlyToOnlineRecipients() {
        User sender = createUser(1L, "sender");
        User online = createUser(2L, "online");
        User offline = createUser(3L, "offline");

        String json = "{\"userId\":1,\"displayName\":\"sender\",\"isTyping\":true}";
        when(messageBuilder.buildMessage(eq(WebSocketMessageType.RECEIVE_TYPING_INDICATOR), eq(10L), any()))
                .thenReturn(json);
        when(connectionManager.isUserOnline(2L)).thenReturn(true);
        when(connectionManager.isUserOnline(3L)).thenReturn(false);

        listener.onTypingIndicator(new TypingIndicatorEvent(10L, sender, true, List.of(online, offline)));

        verify(connectionManager).sendToUser(2L, json);
        verify(connectionManager, never()).sendToUser(eq(3L), any());
    }

    @Test
    void onTypingIndicator_buildsPayloadFromEvent() {
        User sender = createUser(1L, "Alice");
        User online = createUser(2L, "online");

        when(messageBuilder.buildMessage(eq(WebSocketMessageType.RECEIVE_TYPING_INDICATOR), eq(10L), any()))
                .thenReturn("{}");
        when(connectionManager.isUserOnline(2L)).thenReturn(true);

        listener.onTypingIndicator(new TypingIndicatorEvent(10L, sender, false, List.of(online)));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messageBuilder).buildMessage(eq(WebSocketMessageType.RECEIVE_TYPING_INDICATOR), eq(10L),
                payloadCaptor.capture());

        var payload = (org.margin.server.websocket.models.payloads.TypingIndicatorEventPayload) payloadCaptor.getValue();
        assertEquals(1L, payload.userId());
        assertEquals("Alice", payload.displayName());
        assertEquals(false, payload.isTyping());
    }
}
