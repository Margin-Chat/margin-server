package org.margin.server.websocket;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.connection.ClientConnection;
import org.margin.server.users.models.User;

@Slf4j
public class WebSocketClientConnection implements ClientConnection {

    private final Channel channel;
    private final User user;

    public WebSocketClientConnection(Channel channel, User user) {
        this.channel = channel;
        this.user = user;
    }

    @Override
    public void sendMessage(String jsonMessage) {
        if (channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(jsonMessage));
        } else {
            log.warn("Cannot send message, channel inactive for user {}", user.getId());
        }
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public boolean isActive() {
        return channel != null && channel.isActive();
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.close();
        }
    }
}