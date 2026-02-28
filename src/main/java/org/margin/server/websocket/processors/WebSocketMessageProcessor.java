package org.margin.server.websocket.processors;

import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;

public interface WebSocketMessageProcessor<T> {
    WebSocketMessageType getType();
    void process(User user, WebSocketMessageIn<T> message);
}