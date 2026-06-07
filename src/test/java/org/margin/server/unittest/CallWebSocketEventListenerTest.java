package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.calls.events.CallCreatedEvent;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.listeners.CallWebSocketEventListener;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallWebSocketEventListenerTest {

    @Mock
    private WebSocketMessageBuilder webSocketMessageBuilder;
    @Mock
    private ConnectionManager connectionManager;
    @Mock
    private UserService userService;

    @InjectMocks
    private CallWebSocketEventListener listener;

    @Test
    void onCallCreated_sendsCallIdToCaller() {
        Long callerId = 1L;
        Long callId = 500L;
        String mockJson = "{\"callId\":500}";

        when(webSocketMessageBuilder.buildMessage(eq(WebSocketMessageType.CALL_CREATED), eq(callerId), any()))
                .thenReturn(mockJson);

        listener.onCallCreated(new CallCreatedEvent(callerId, callId));

        verify(connectionManager).sendToUser(callerId, mockJson);
    }
}