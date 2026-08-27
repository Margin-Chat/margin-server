package org.margin.server.websocket;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.presence.PresenceService;
import org.margin.server.users.api.UserLookup;
import org.margin.server.meetings.api.MeetingAdmissionCommands;
import org.margin.server.meetings.api.MeetingLobbyRegistry;
import org.margin.server.meetings.security.MeetingGuestPrincipal;
import org.margin.server.meetings.api.MeetingGuestTokens;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.websocket.connection.ClientConnection;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.processors.WebSocketMessageProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;


@Slf4j
public class WebSocketHandler extends SimpleChannelInboundHandler<Object> {

    public static final WebSocketCloseStatus UNAUTHORIZED_CLOSE_STATUS =
            new WebSocketCloseStatus(4001, "unauthorized");

    private final Executor dbExecutor;
    private final JwtService jwtService;
    private final ConnectionManager connectionManager;
    private final PresenceService presenceService;
    private final UserLookup userLookup;
    private final MeetingGuestTokens guestTokenService;
    private final MeetingLobbyRegistry lobbyRegistry;
    private final MeetingAdmissionCommands admissionCommands;
    private final Map<WebSocketMessageType, WebSocketMessageProcessor<Object>> dispatch;

    @SuppressWarnings("unchecked")
    public WebSocketHandler(Executor dbExecutor,
                            JwtService jwtService,
                            ConnectionManager connectionManager,
                            PresenceService presenceService,
                            UserLookup userLookup,
                            MeetingGuestTokens guestTokenService,
                            MeetingLobbyRegistry lobbyRegistry,
                            MeetingAdmissionCommands admissionCommands,
                            List<WebSocketMessageProcessor<?>> processors) {
        this.dbExecutor = dbExecutor;
        this.jwtService = jwtService;
        this.connectionManager = connectionManager;
        this.presenceService = presenceService;
        this.userLookup = userLookup;
        this.guestTokenService = guestTokenService;
        this.lobbyRegistry = lobbyRegistry;
        this.admissionCommands = admissionCommands;
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

        if (!isWebSocketUpgrade(req)) {
            sendStatusAndClose(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        Optional<AuthenticatedUser> optionalUser = jwtService.extractAndValidateJwtTokenFromWebSocket(req.uri());
        Optional<MeetingGuestPrincipal> optionalGuest = optionalUser.isPresent()
                ? Optional.empty()
                : guestTokenService.parse(extractToken(req.uri()));

        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                buildWsUrl(req), null, true, 65536);
        WebSocketServerHandshaker handshaker = factory.newHandshaker(req);

        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel())
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }

        ctx.channel().attr(WebSocketAttributes.HANDSHAKER).set(handshaker);

        if (optionalUser.isEmpty() && optionalGuest.isEmpty()) {
            rejectUnauthenticated(ctx, req, handshaker);
            return;
        }

        if (optionalUser.isEmpty()) {
            MeetingGuestPrincipal guest = optionalGuest.get();
            ctx.channel().attr(WebSocketAttributes.MEETING_GUEST).set(guest);
            handshaker.handshake(ctx.channel(), req).addListener(future -> {
                if (future.isSuccess()) {
                    lobbyRegistry.register(guest.meetingId(), guest.userId(), ctx.channel());
                }
            });
            return;
        }

        AuthenticatedUser user = optionalUser.get();
        ctx.channel().attr(WebSocketAttributes.USER).set(user);

        handshaker.handshake(ctx.channel(), req).addListener(future -> {
            if (future.isSuccess()) {
                onConnectionEstablished(ctx, user);
            }
        });
    }

    /**
     * Completes the handshake and immediately closes with {@link #UNAUTHORIZED_CLOSE_STATUS}
     * rather than failing the upgrade with a 401. Browser {@code WebSocket} clients cannot read
     * the status of a response that never upgraded — they only ever see close code 1006, which is
     * indistinguishable from a network fault, so they retry forever against a dead credential.
     * A close code they can read lets them stop and send the user to log in.
     */
    private void rejectUnauthenticated(ChannelHandlerContext ctx,
                                       FullHttpRequest req,
                                       WebSocketServerHandshaker handshaker) {
        handshaker.handshake(ctx.channel(), req).addListener(future -> {
            if (future.isSuccess()) {
                ctx.writeAndFlush(new CloseWebSocketFrame(UNAUTHORIZED_CLOSE_STATUS))
                        .addListener(ChannelFutureListener.CLOSE);
            } else {
                ctx.close();
            }
        });
    }

    private void onConnectionEstablished(ChannelHandlerContext ctx, AuthenticatedUser user) {
        ClientConnection connection = new WebSocketClientConnection(ctx.channel(), user);
        connectionManager.addConnection(user, connection);
        presenceService.userConnected(user.id());
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        switch (frame) {
            case CloseWebSocketFrame close -> ctx.channel().attr(WebSocketAttributes.HANDSHAKER).get()
                    .close(ctx.channel(), close.retain());
            case PingWebSocketFrame ping -> ctx.writeAndFlush(new PongWebSocketFrame(ping.content().retain()));
            case PongWebSocketFrame ignored -> {
                // pong from client in response to our ping; IdleStateHandler already saw the read
            }
            default -> log.warn("Unhandled frame type: {}", frame.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    public void handleWebSocketMessage(ChannelHandlerContext ctx, WebSocketMessageIn<?> message) {
        if (message.getType() == WebSocketMessageType.PING) {
            ctx.writeAndFlush(new TextWebSocketFrame("{\"type\":\"PONG\"}"));
            return;
        }

        MeetingGuestPrincipal guest = ctx.channel().attr(WebSocketAttributes.MEETING_GUEST).get();
        if (guest != null) {
            handleGuestMessage(guest, message);
            return;
        }

        AuthenticatedUser user = ctx.channel().attr(WebSocketAttributes.USER).get();
        if (user == null) {
            ctx.close();
            return;
        }

        WebSocketMessageProcessor<Object> processor = dispatch.get(message.getType());
        if (processor == null) {
            log.warn("No processor for type: {}", message.getType());
            return;
        }

        try {
            processor.process(user, (WebSocketMessageIn<Object>) message);
        } catch (Exception t) {
            log.error("Processor {} failed for user {}", message.getType(), user.id(), t);
        }
    }

    private void handleGuestMessage(MeetingGuestPrincipal guest, WebSocketMessageIn<?> message) {
        switch (message.getType()) {
            case MEETING_KNOCK -> admissionCommands.knock(guest.meetingId(), guest.userId());
            case MEETING_LEAVE_LOBBY -> admissionCommands.leaveLobby(guest.meetingId(), guest.userId());
            default -> log.warn("Guest {} sent unsupported message type {}", guest.userId(), message.getType());
        }
    }

    private String extractToken(String uri) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        List<String> values = decoder.parameters().get("token");
        return values == null || values.isEmpty() ? "" : values.getFirst();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent idleEvent) {
            if (idleEvent.state() == IdleState.WRITER_IDLE) {
                ctx.writeAndFlush(new PingWebSocketFrame());
            } else if (idleEvent.state() == IdleState.READER_IDLE) {
                AuthenticatedUser user = ctx.channel().attr(WebSocketAttributes.USER).get();
                log.info("Closing idle websocket connection for user {}",
                        user != null ? user.id() : "<unauthenticated>");
                ctx.close();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        MeetingGuestPrincipal guest = ctx.channel().attr(WebSocketAttributes.MEETING_GUEST).get();
        if (guest != null) {
            lobbyRegistry.unregister(guest.meetingId(), guest.userId());
        }

        AuthenticatedUser user = ctx.channel().attr(WebSocketAttributes.USER).get();
        if (user != null) {
            boolean lastSession = connectionManager.removeConnection(user, ctx.channel());
            if (lastSession) {
                presenceService.userDisconnected(user.id());
                dbExecutor.execute(() -> stampLastSeen(user.id()));
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket error", cause);
        ctx.close();
    }

    private static boolean isWebSocketUpgrade(FullHttpRequest req) {
        return req.headers().containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)
                && req.headers().containsValue(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true);
    }

    private static void sendStatusAndClose(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private String buildWsUrl(FullHttpRequest req) {
        String host = req.headers().get("Host", "localhost:8081");
        return "ws://" + host + req.uri();
    }

    private void stampLastSeen(Long userId) {
        try {
            userLookup.markLastSeen(userId, Instant.now());
        } catch (Exception e) {
            log.warn("Failed to stamp lastSeenAt for user {}", userId, e);
        }
    }
}