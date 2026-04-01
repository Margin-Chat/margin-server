package org.margin.server.websocket;

import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.util.AttributeKey;
import org.margin.server.users.models.User;

public class WebSocketAttributes {
    private WebSocketAttributes() {
    }

    public static final AttributeKey<User> USER = AttributeKey.valueOf("user");
    public static final AttributeKey<WebSocketServerHandshaker> HANDSHAKER =
            AttributeKey.valueOf("handshaker");
}