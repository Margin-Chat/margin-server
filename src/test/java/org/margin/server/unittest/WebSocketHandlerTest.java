package org.margin.server.unittest;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.presence.PresenceService;
import org.margin.server.users.api.UserLookup;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.websocket.WebSocketAttributes;
import org.margin.server.websocket.WebSocketHandler;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.processors.WebSocketMessageProcessor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.margin.server.unittest.utils.UserTestUtils.*;

@ExtendWith(MockitoExtension.class)
class WebSocketHandlerTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private ConnectionManager connectionManager;
    @Mock
    private PresenceService presenceService;
    @Mock
    private UserLookup userLookup;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private Channel channel;
    @Mock
    private Attribute<AuthenticatedUser> userAttribute;

    private WebSocketMessageProcessor<Object> testProcessor;
    private WebSocketHandler handler;
    private Executor immediateExecutor;

    @BeforeEach
    void setUp() {
        immediateExecutor = Runnable::run;

        testProcessor = new WebSocketMessageProcessor<>() {
            @Override
            public WebSocketMessageType getType() {
                return WebSocketMessageType.SEND_MESSAGE;
            }

            @Override
            public void process(AuthenticatedUser user, WebSocketMessageIn<Object> message) {
                // Empty for SQ
            }
        };
        testProcessor = spy(testProcessor);

        handler = new WebSocketHandler(
                immediateExecutor,
                jwtService,
                connectionManager,
                presenceService,
                userLookup,
                List.of(testProcessor)
        );

        clearInvocations(testProcessor);

        when(ctx.channel()).thenReturn(channel);
        when(channel.attr(WebSocketAttributes.USER)).thenReturn(userAttribute);
    }

    @Test
    void handleWebSocketMessage_dispatchesToCorrectProcessor() {
        AuthenticatedUser user = authUser(1L, "sender");
        when(userAttribute.get()).thenReturn(user);

        WebSocketMessageIn<Object> message = new WebSocketMessageIn<>();
        message.setType(WebSocketMessageType.SEND_MESSAGE);
        message.setPayload("Hello");

        handler.handleWebSocketMessage(ctx, message);

        verify(testProcessor).process(eq(user), eq(message));
    }

    @Test
    void handleWebSocketMessage_closesChannelWhenUserIsNull() {
        when(userAttribute.get()).thenReturn(null);

        WebSocketMessageIn<Object> message = new WebSocketMessageIn<>();
        message.setType(WebSocketMessageType.SEND_MESSAGE);

        handler.handleWebSocketMessage(ctx, message);

        verify(ctx).close();
        verifyNoInteractions(testProcessor);
    }

    @Test
    void handleWebSocketMessage_ignoresUnknownMessageType() {
        AuthenticatedUser user = authUser(1L, "sender");
        when(userAttribute.get()).thenReturn(user);

        WebSocketMessageIn<Object> message = new WebSocketMessageIn<>();
        message.setType(WebSocketMessageType.CALL_OFFER);

        handler.handleWebSocketMessage(ctx, message);

        verifyNoInteractions(testProcessor);
    }

    @Test
    void channelInactive_removesConnectionAndNotifiesOffline() {
        AuthenticatedUser user = authUser(1L, "sender");
        when(userAttribute.get()).thenReturn(user);
        when(connectionManager.removeConnection(user, channel)).thenReturn(true);

        handler.channelInactive(ctx);

        verify(connectionManager).removeConnection(user, channel);
        verify(presenceService).userDisconnected(user.id());
        verify(userLookup).markLastSeen(eq(1L), any());
    }

    @Test
    void channelInactive_swallowsLastSeenFailures() {
        AuthenticatedUser user = authUser(1L, "sender");
        when(userAttribute.get()).thenReturn(user);
        when(connectionManager.removeConnection(user, channel)).thenReturn(true);
        doThrow(new RuntimeException("db down")).when(userLookup).markLastSeen(eq(1L), any());

        assertDoesNotThrow(() -> handler.channelInactive(ctx));

        verify(presenceService).userDisconnected(user.id());
    }

    @Test
    void channelInactive_doesNotNotifyWhenChannelMismatch() {
        AuthenticatedUser user = authUser(1L, "sender");
        when(userAttribute.get()).thenReturn(user);
        when(connectionManager.removeConnection(user, channel)).thenReturn(false);

        handler.channelInactive(ctx);

        verify(connectionManager).removeConnection(user, channel);
        verifyNoInteractions(presenceService);
    }

    @Test
    void channelInactive_doesNothingWhenUserIsNull() {
        when(userAttribute.get()).thenReturn(null);

        handler.channelInactive(ctx);

        verifyNoInteractions(connectionManager);
        verifyNoInteractions(presenceService);
    }

}