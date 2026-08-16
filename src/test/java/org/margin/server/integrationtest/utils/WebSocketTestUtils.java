package org.margin.server.integrationtest.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Component
public class WebSocketTestUtils {

    private static JwtService jwtService;
    private static WebSocketServer webSocketServer;
    private static String jwtSecret;

    @Autowired
    public WebSocketTestUtils(JwtService jwtService,
                              WebSocketServer webSocketServer,
                              @Value("${jwt.secret}") String jwtSecret) {
        WebSocketTestUtils.jwtService = jwtService;
        WebSocketTestUtils.webSocketServer = webSocketServer;
        WebSocketTestUtils.jwtSecret = jwtSecret;
    }

    public static WebSocket connect(User user) throws Exception {
        return connect(user, new WebSocket.Listener() {
        });
    }

    public static WebSocket connect(User user, WebSocket.Listener listener) throws Exception {
        String token = jwtService.generateToken(user.getEmail(), user.getId(), AuthTestUtils.securityOf(user).getTokenVersion());
        int wsPort = webSocketServer.awaitBoundPort(5000);
        CompletableFuture<Void> connected = new CompletableFuture<>();

        WebSocket ws = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(
                        URI.create("ws://localhost:" + wsPort + "/ws?token=" + token),
                        new WebSocket.Listener() {
                            @Override
                            public void onOpen(WebSocket webSocket) {
                                connected.complete(null);
                                listener.onOpen(webSocket);
                            }

                            @Override
                            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                return listener.onText(webSocket, data, last);
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        connected.get(5, TimeUnit.SECONDS);
        return ws;
    }

    /** Outcome of a handshake the server refused: the close code and reason it sent. */
    public record Rejection(int statusCode, String reason) {
    }

    /**
     * Opens {@code /ws} with the given token (pass {@code null} to omit the query parameter
     * entirely) and waits for the server to close the connection.
     */
    public static Rejection connectExpectingRejection(String token) throws Exception {
        int wsPort = webSocketServer.awaitBoundPort(5000);
        CompletableFuture<Rejection> closed = new CompletableFuture<>();

        String query = token == null ? "" : "?token=" + token;
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(
                        URI.create("ws://localhost:" + wsPort + "/ws" + query),
                        new WebSocket.Listener() {
                            @Override
                            public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                                closed.complete(new Rejection(statusCode, reason));
                                return null;
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        return closed.get(5, TimeUnit.SECONDS);
    }

    /** A correctly signed token for {@code user} that expired {@code age} ago. */
    public static String expiredToken(User user, Duration age) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant expiredAt = Instant.now().minus(age);
        return Jwts.builder()
                .claims(Map.of(
                        "userId", user.getId(),
                        "tv", AuthTestUtils.securityOf(user).getTokenVersion()))
                .subject(user.getEmail())
                .issuedAt(Date.from(expiredAt.minus(Duration.ofDays(7))))
                .expiration(Date.from(expiredAt))
                .signWith(key)
                .compact();
    }

    public static String validToken(User user) {
        return jwtService.generateToken(
                user.getEmail(), user.getId(), AuthTestUtils.securityOf(user).getTokenVersion());
    }

    public static WebSocket.Listener listenerThatCompletes(CompletableFuture<String> future, String containsText) {
        return new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                String text = data.toString();
                if (text.contains(containsText)) {
                    future.complete(text);
                }
                ws.request(1);
                return null;
            }
        };
    }

    public static void close(WebSocket... sockets) {
        for (WebSocket ws : sockets) {
            if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    public static String wsFrame(String type, long recipientId, String payload) {
        return """
                {
                  "type": "%s", 1   1
                  "recipientId": %d,
                  "payload": "%s"
                }
                """.formatted(type, recipientId, payload);
    }

    public static String wsFrame(String type, long recipientId, Object payload) {
        String payloadJson;
        if (payload instanceof String s) {
            payloadJson = "\"" + s + "\"";
        } else {
            payloadJson = payload.toString();
        }
        return """
                {
                  "type": "%s",
                  "recipientId": %d,
                  "payload": %s
                }
                """.formatted(type, recipientId, payloadJson);
    }
}