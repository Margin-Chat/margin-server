package org.margin.server.connection;

import org.margin.server.users.models.User;

public interface ClientConnection {

    void sendMessage(String jsonMessage);

    User getUser();

    boolean isActive();

    void close();
}