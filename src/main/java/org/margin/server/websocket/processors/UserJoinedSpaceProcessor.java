package org.margin.server.websocket.processors;

import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;

public class UserJoinedSpaceProcessor implements WebSocketMessageProcessor<String> {
    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.USER_JOINED_SPACE;
    }

    @Override
    public void process(AuthenticatedUser user, WebSocketMessageIn<String> message) {

    }
}
