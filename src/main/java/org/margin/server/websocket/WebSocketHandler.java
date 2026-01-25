package org.margin.server.websocket;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.social.communication.messages.models.DirectMessage;
import org.margin.server.users.services.UserService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.payloads.IncomingCallCandidatePayload;
import org.margin.server.websocket.models.payloads.IncomingCallEndPayload;
import org.margin.server.websocket.models.payloads.IncomingCallOfferPayload;
import org.margin.server.websocket.models.payloads.IncomingCallResponsePayload;
import org.margin.server.websocket.services.WebSocketClientService;

import java.util.Optional;

@Slf4j
@ChannelHandler.Sharable
public class WebSocketHandler extends SimpleChannelInboundHandler<Object> {
    private final JwtService jwtService;
    private final WebSocketClientService clientService;
    private final UserService userService;

    public WebSocketHandler(JwtService jwtService,
                            WebSocketClientService clientService,
                            UserService userService) {
        this.jwtService = jwtService;
        this.clientService = clientService;
        this.userService = userService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object message) {
        switch (message) {
            case FullHttpRequest httpRequest -> handleHttpRequest(ctx, httpRequest);
            case WebSocketMessageIn<?> webSocketMessage -> handleWebSocketMessage(ctx, webSocketMessage);
            case WebSocketFrame webSocketFrame -> handleWebSocketFrame(ctx, webSocketFrame);
            default -> throw new IllegalStateException("Unexpected value: " + message);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.decoderResult().isSuccess()) {
            log.warn("Bad HTTP request from {}", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }

        String uri = req.uri();
        if (!uri.startsWith("/ws")) {
            log.warn("Invalid WebSocket path: {}", uri);
            ctx.close();
            return;
        }

        Optional<User> optionalUser = jwtService.extractAndValidateJwtTokenFromWebSocket(uri);

        if (optionalUser.isEmpty()) {
            ctx.close();
            return;
        }

        User user = optionalUser.get();
        ctx.channel().attr(WebSocketAttributes.USER).set(user);

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                "ws://localhost:8081/ws", null, true, 65536);
        WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(req);

        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            ctx.channel().attr(WebSocketAttributes.HANDSHAKER).set(handshaker);

            handshaker.handshake(ctx.channel(), req).addListener(future -> {
                if (future.isSuccess()) {
                    clientService.addClient(user.getId(), ctx.channel());
                    clientService.broadcastUserLogin(user);
                }
            });
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        User user = ctx.channel().attr(WebSocketAttributes.USER).get();
        WebSocketServerHandshaker handshaker = ctx.channel().attr(WebSocketAttributes.HANDSHAKER).get();

        switch (frame) {
            case CloseWebSocketFrame closeFrame -> {
                log.info("Client requested close: {}", user.getId());
                handshaker.close(ctx.channel(), closeFrame.retain());
            }
            case PingWebSocketFrame pingFrame ->
                    ctx.writeAndFlush(new PongWebSocketFrame(pingFrame.content().retain()));
            case TextWebSocketFrame ignored -> log.warn("Received unprocessed TextWebSocketFrame - decoder may have failed");
            default -> log.warn("Unhandled frame type: {}", frame.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleWebSocketMessage(ChannelHandlerContext ctx, WebSocketMessageIn<?> message) {
        User user = ctx.channel().attr(WebSocketAttributes.USER).get();
        log.debug("Received message from {}: type={}", user, message.getType());

        switch (message.getType()) {
            case SEND_DIRECT_MESSAGE -> handleDirectMessage(user,
                    (WebSocketMessageIn<String>) message);
            case SEND_CHANNEL_MESSAGE -> handleChannelMessage(user,
                    (WebSocketMessageIn<String>) message);
            case CALL_OFFER -> clientService.sendCallOffer(user,
                    (WebSocketMessageIn<IncomingCallOfferPayload>) message);
            case CALL_RESPONSE -> clientService.sendCallResponse(
                    (WebSocketMessageIn<IncomingCallResponsePayload>) message);
            case CALL_CANDIDATE -> clientService.sendCallCandidate(
                    (WebSocketMessageIn<IncomingCallCandidatePayload>) message);
            case CALL_END -> clientService.sendCallEnd(
                    (WebSocketMessageIn<IncomingCallEndPayload>) message);
        }
    }

    private void handleDirectMessage(User user, WebSocketMessageIn<String> message) {
        DirectMessage directMessage = getChatMessage(user, message.getRecipientId().toString(), message.getPayload());
        clientService.sendMessageToUser(directMessage);
    }

    private void handleChannelMessage(User user, WebSocketMessageIn<String> message) {
        clientService.sendMessageToChannel(user, message.getRecipientId(), message.getPayload());
    }

    private DirectMessage getChatMessage(User user, String toUserIdIdentifier, String messageText) {
        String[] parts = toUserIdIdentifier.split("@", 2);
        User toUser = userService.getById(Long.parseLong(parts[0]));

        return new DirectMessage(
                user.getId(),
                toUser.getId(),
                messageText
        );
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        User user = ctx.channel().attr(WebSocketAttributes.USER).get();
        if (user != null) {
            clientService.removeClient(user.getId());
            clientService.broadcastUserLogout(user);
            log.info("User {} disconnected", user.getId());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        User user = ctx.channel().attr(WebSocketAttributes.USER).get();
        log.error("WebSocket error for user {}: {}", user.getId(), cause.getMessage());
        ctx.close();
    }
}