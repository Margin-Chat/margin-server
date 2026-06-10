package org.margin.server.integrationtest.utils;

import org.margin.server.authentication.services.JwtService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Component
public class WebSocketTestUtils {

    private static JwtService jwtService;
    private static WebSocketServer webSocketServer;

    @Autowired
    public WebSocketTestUtils(JwtService jwtService, WebSocketServer webSocketServer) {
        WebSocketTestUtils.jwtService = jwtService;
        WebSocketTestUtils.webSocketServer = webSocketServer;
    }

    public static WebSocket connect(User user) throws Exception {
        return connect(user, new WebSocket.Listener() {
        });
    }

    public static WebSocket connect(User user, WebSocket.Listener listener) throws Exception {
        String token = jwtService.generateToken(user.getEmail(), user.getId(), user.getSecurity().getTokenVersion());
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