package org.margin.server.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.connection.ClientConnection;
import org.margin.server.connection.ConnectionManager;
import org.margin.server.presence.PresenceService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.handlers.CallHandler;
import org.margin.server.websocket.handlers.MessageHandler;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.payloads.*;

import java.util.Optional;


@Slf4j
public class WebSocketHandler extends SimpleChannelInboundHandler<Object> {

    private final JwtService jwtService;
    private final ConnectionManager connectionManager;
    private final MessageHandler messageHandler;
    private final CallHandler callHandler;
    private final PresenceService presenceService;

    public WebSocketHandler(JwtService jwtService,
                            ConnectionManager connectionManager,
                            MessageHandler messageHandler,
                            CallHandler callHandler,
                            PresenceService presenceService) {
        this.jwtService = jwtService;
        this.connectionManager = connectionManager;
        this.messageHandler = messageHandler;
        this.callHandler = callHandler;
        this.presenceService = presenceService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object message) {
        switch (message) {
            case FullHttpRequest httpRequest -> handleHttpRequest(ctx, httpRequest);
            case WebSocketMessageIn<?> wsMessage -> handleWebSocketMessage(ctx, wsMessage);
            case WebSocketFrame frame -> handleWebSocketFrame(ctx, frame);
            default -> throw new IllegalStateException("Unexpected message type: " + message);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.decoderResult().isSuccess() || !req.uri().startsWith("/ws")) {
            ctx.close();
            return;
        }

        Optional<User> optionalUser = jwtService.extractAndValidateJwtTokenFromWebSocket(req.uri());
        if (optionalUser.isEmpty()) {
            ctx.close();
            return;
        }

        User user = optionalUser.get();
        ctx.channel().attr(WebSocketAttributes.USER).set(user);

        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                "ws://localhost:8081/ws", null, true, 65536);
        WebSocketServerHandshaker handshaker = factory.newHandshaker(req);

        if (handshaker != null) {
            ctx.channel().attr(WebSocketAttributes.HANDSHAKER).set(handshaker);
            handshaker.handshake(ctx.channel(), req).addListener(future -> {
                if (future.isSuccess()) {
                    onConnectionEstablished(ctx, user);
                }
            });
        }
    }

    private void onConnectionEstablished(ChannelHandlerContext ctx, User user) {
        ClientConnection connection = new WebSocketClientConnection(ctx.channel(), user);
        connectionManager.addConnection(user, connection);
        presenceService.userConnected(user);
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        switch (frame) {
            case CloseWebSocketFrame close ->
                    ctx.channel().attr(WebSocketAttributes.HANDSHAKER).get()
                            .close(ctx.channel(), close.retain());
            case PingWebSocketFrame ping ->
                    ctx.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
            default ->
                    log.warn("Unhandled frame type: {}", frame.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleWebSocketMessage(ChannelHandlerContext ctx, WebSocketMessageIn<?> message) {
        User user = ctx.channel().attr(WebSocketAttributes.USER).get();

        switch (message.getType()) {
            case SEND_DIRECT_MESSAGE ->
                    messageHandler.handleDirectMessage(user, (WebSocketMessageIn<String>) message);
            case SEND_CHANNEL_MESSAGE ->
                    messageHandler.handleChannelMessage(user, (WebSocketMessageIn<String>) message);
            case CALL_OFFER ->
                    callHandler.handleCallOffer(user, (WebSocketMessageIn<IncomingCallOfferPayload>) message);
            case CALL_RESPONSE ->
                    callHandler.handleCallResponse((WebSocketMessageIn<IncomingCallResponsePayload>) message);
            case CALL_CANDIDATE ->
                    callHandler.handleCallCandidate((WebSocketMessageIn<IncomingCallCandidatePayload>) message);
            case CALL_END ->
                    callHandler.handleCallEnd((WebSocketMessageIn<IncomingCallEndPayload>) message);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        User user = ctx.channel().attr(WebSocketAttributes.USER).get();
        if (user != null) {
            connectionManager.removeConnection(user);
            presenceService.userDisconnected(user);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket error", cause);
        ctx.close();
    }
}