package org.margin.server.websocket.connection;

import io.netty.channel.Channel;
import org.margin.server.shared.security.AuthenticatedUser;

public interface ClientConnection {

    void sendMessage(String jsonMessage);

    AuthenticatedUser getUser();

    boolean isActive();

    void close();

    Channel getChannel();
}