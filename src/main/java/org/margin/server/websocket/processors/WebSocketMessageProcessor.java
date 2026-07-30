package org.margin.server.websocket.processors;

import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;

public interface WebSocketMessageProcessor<T> {
    WebSocketMessageType getType();
    void process(AuthenticatedUser user, WebSocketMessageIn<T> message);
}