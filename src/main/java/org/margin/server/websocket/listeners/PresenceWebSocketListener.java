package org.margin.server.websocket.listeners;

import org.margin.server.presence.events.UserConnectedEvent;
import org.margin.server.presence.events.UserDisconnectedEvent;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PresenceWebSocketListener {

    private final WebSocketMessageBuilder messageBuilder;
    private final ConnectionManager connectionManager;
    private final UserService userService;

    public PresenceWebSocketListener(WebSocketMessageBuilder messageBuilder,
                                     ConnectionManager connectionManager,
                                     UserService userService) {
        this.messageBuilder = messageBuilder;
        this.connectionManager = connectionManager;
        this.userService = userService;
    }

    @EventListener
    public void onUserConnected(UserConnectedEvent event) {
        User user = userService.getById(event.getUserId());
        String json = messageBuilder.buildMessage(WebSocketMessageType.USER_LOGIN, user.getId(),
                new UserDTO(user, connectionManager.isUserOnline(user.getId())));
        connectionManager.broadcast(json, user.getId());
    }

    @EventListener
    public void onUserDisconnected(UserDisconnectedEvent event) {
        User user = userService.getById(event.getUserId());
        String json = messageBuilder.buildMessage(WebSocketMessageType.USER_LOGOUT, user.getId(),
                new UserDTO(user, connectionManager.isUserOnline(user.getId())));
        connectionManager.broadcast(json, user.getId());
    }
}
