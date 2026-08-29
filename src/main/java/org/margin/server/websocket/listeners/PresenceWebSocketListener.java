package org.margin.server.websocket.listeners;

import org.margin.server.presence.events.UserConnectedEvent;
import org.margin.server.presence.events.UserDisconnectedEvent;
import org.margin.server.users.api.UserLookup;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PresenceWebSocketListener {

    private final WebSocketMessageBuilder messageBuilder;
    private final ConnectionManager connectionManager;
    private final UserLookup userLookup;

    public PresenceWebSocketListener(WebSocketMessageBuilder messageBuilder,
                                     ConnectionManager connectionManager,
                                     UserLookup userLookup) {
        this.messageBuilder = messageBuilder;
        this.connectionManager = connectionManager;
        this.userLookup = userLookup;
    }

    @EventListener
    public void onUserConnected(UserConnectedEvent event) {
        Long userId = event.getUserId();
        String json = messageBuilder.buildMessage(WebSocketMessageType.USER_LOGIN, userId, userLookup.dtoOf(userId));
        connectionManager.broadcast(json, userId);
    }

    @EventListener
    public void onUserDisconnected(UserDisconnectedEvent event) {
        Long userId = event.getUserId();
        String json = messageBuilder.buildMessage(WebSocketMessageType.USER_LOGOUT, userId, userLookup.dtoOf(userId));
        connectionManager.broadcast(json, userId);
    }
}
