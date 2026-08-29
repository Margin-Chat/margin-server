package org.margin.server.websocket.listeners;

import org.margin.server.notifications.events.NotificationDeliveryEvent;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationWebSocketEventListener {

    private final WebSocketMessageBuilder messageBuilder;
    private final ConnectionManager connectionManager;
    private final UserService userService;

    public NotificationWebSocketEventListener(WebSocketMessageBuilder messageBuilder,
                                              ConnectionManager connectionManager,
                                              UserService userService) {
        this.messageBuilder = messageBuilder;
        this.connectionManager = connectionManager;
        this.userService = userService;
    }

    @EventListener
    public void onNotificationDelivery(NotificationDeliveryEvent event) {
        var notification = event.getNotification();
        if (connectionManager.isUserOnline(notification.getRecipientId())) {
            UserDTO sender = notification.getSenderId() == null ? null
                    : new UserDTO(userService.getById(notification.getSenderId()), false);
            String json = messageBuilder.notification(notification, sender);
            connectionManager.sendToUser(notification.getRecipientId(), json);
        }
    }
}