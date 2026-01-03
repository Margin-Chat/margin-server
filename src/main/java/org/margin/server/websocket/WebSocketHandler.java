package org.margin.server.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.social.models.DirectMessage;
import org.margin.server.users.UserService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.services.WebSocketClientService;

import java.util.Optional;

@Slf4j
@ChannelHandler.Sharable
public class WebSocketHandler extends SimpleChannelInboundHandler<Object> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
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
                log.info("Client requested close: {}", user);
                handshaker.close(ctx.channel(), closeFrame);
            }
            case PingWebSocketFrame pingFrame -> {
                ctx.writeAndFlush(new PongWebSocketFrame(pingFrame.content().retain()));
            }
            case TextWebSocketFrame textFrame -> {
                handleTextWebSocketFrame(ctx, textFrame);
            }
            default -> throw new IllegalStateException("Unexpected value: " + frame);
        }
    }

    private void handleTextWebSocketFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        User user = ctx.channel().attr(WebSocketAttributes.USER).get();
        String payload = frame.text();
        log.debug("Received message from {}: {}", user, payload);

        try {
            JsonNode message = parseMessage(payload);
            WebSocketMessageType type = getMessageType(message);

            switch (type) {
                case SEND_DIRECT_MESSAGE -> handleDirectMessage(user, message);
                case SEND_SPACE_MESSAGE -> handleChannelMessage(user, message);
                case CALL_OFFER -> clientService.sendCallOffer(user, message);
                case CALL_RESPONSE -> clientService.sendCallResponse(user, message);
                case CALL_CANDIDATE -> clientService.sendCallCandidate(user, message);
                case CALL_END -> clientService.sendCallEnd(user, message);
            }

        } catch (IllegalArgumentException e) {
            log.warn("Unknown message type from user {}: {}", user.getId(), e.getMessage());
        } catch (Exception e) {
            log.error("Error handling message from user {}: {}", user.getId(), e.getMessage());
        }
    }

    private JsonNode parseMessage(String payload) throws Exception {
        return MAPPER.readTree(payload);
    }

    private WebSocketMessageType getMessageType(JsonNode message) {
        return WebSocketMessageType.valueOf(message.get("type").asText());
    }

    private void handleDirectMessage(User user, JsonNode message) {
        String toUserIdIdentifier = message.get("toUserId").asText();
        String messageText = message.get("message").asText();

        DirectMessage directMessage = getChatMessage(user, toUserIdIdentifier, messageText);
        clientService.sendMessageToUser(directMessage);
    }

    private void handleChannelMessage(User user, JsonNode message) {
        log.info("Received channel message from {}", user.getUsername());
    }

    private DirectMessage getChatMessage(User user, String toUserIdIdentifier, String messageText) {
        String[] parts = toUserIdIdentifier.split("@", 2);
        User toUser = userService.getById(Long.parseLong(parts[0]));
        String toUserServer = parts.length > 1 ? parts[1] : null;

        return new DirectMessage(
                user.getId(),
                toUser.getId(),
                user.getServerDomain(),
                toUserServer,
                messageText
        );
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        User user = ctx.channel().attr(WebSocketAttributes.USER).get();
        if (user != null) {
            clientService.removeClient(user.getId());
            clientService.broadcastUserLogout(user);
            log.info("User {} disconnected", user);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        User user = ctx.channel().attr(WebSocketAttributes.USER).get();
        log.error("WebSocket error for user {}: {}", user, cause.getMessage());
        ctx.close();
    }
}