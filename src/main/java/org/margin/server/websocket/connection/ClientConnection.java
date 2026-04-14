package org.margin.server.websocket.connection;

import io.netty.channel.Channel;
import org.margin.server.users.models.User;

public interface ClientConnection {

    void sendMessage(String jsonMessage);

    User getUser();

    boolean isActive();

    void close();

    Channel getChannel();
}