package org.margin.server.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.presence.PresenceService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.connection.ClientConnection;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.processors.WebSocketMessageProcessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;


@Slf4j
public class WebSocketHandler extends SimpleChannelInboundHandler<Object> {
    private final Executor dbExecutor;
    private final JwtService jwtService;
    private final ConnectionManager connectionManager;
    private final PresenceService presenceService;
    private final Map<WebSocketMessageType, WebSocketMessageProcessor<Object>> dispatch;

    @SuppressWarnings("unchecked")
    public WebSocketHandler(Executor dbExecutor,
                            JwtService jwtService,
                            ConnectionManager connectionManager,
                            PresenceService presenceService,
                            List<WebSocketMessageProcessor<?>> processors) {
        this.dbExecutor = dbExecutor;
        this.jwtService = jwtService;
        this.connectionManager = connectionManager;
        this.presenceService = presenceService;
        this.dispatch = processors.stream()
                .collect(Collectors.toMap(
                        WebSocketMessageProcessor::getType,
                        p -> (WebSocketMessageProcessor<Object>) p
                ));
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
            var response = new io.netty.handler.codec.http.DefaultFullHttpResponse(
                    io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                    io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED
            );
            ctx.writeAndFlush(response).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
            return;
        }

        User user = optionalUser.get();
        ctx.channel().attr(WebSocketAttributes.USER).set(user);

        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                buildWsUrl(req), null, true, 65536);
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
            case CloseWebSocketFrame close -> ctx.channel().attr(WebSocketAttributes.HANDSHAKER).get()
                    .close(ctx.channel(), close.retain());
            case PingWebSocketFrame ping -> ctx.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
            default -> log.warn("Unhandled frame type: {}", frame.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    public void handleWebSocketMessage(ChannelHandlerContext ctx, WebSocketMessageIn<?> message) {
        if (message.getType() == WebSocketMessageType.PING) {
            ctx.writeAndFlush(new TextWebSocketFrame("{\"type\":\"PONG\"}"));
            return;
        }

        User user = ctx.channel().attr(WebSocketAttributes.USER).get();
        if (user == null) {
            ctx.close();
            return;
        }

        WebSocketMessageProcessor<Object> processor = dispatch.get(message.getType());
        if (processor == null) {
            log.warn("No processor for type: {}", message.getType());
            return;
        }

        dbExecutor.execute(() -> processor.process(user, (WebSocketMessageIn<Object>) message));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        User user = ctx.channel().attr(WebSocketAttributes.USER).get();
        if (user != null) {
            boolean lastSession = connectionManager.removeConnection(user, ctx.channel());
            if (lastSession) {
                presenceService.userDisconnected(user);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket error", cause);
        ctx.close();
    }

    private String buildWsUrl(FullHttpRequest req) {
        String host = req.headers().get("Host", "localhost:8081");
        return "ws://" + host + req.uri();
    }
}