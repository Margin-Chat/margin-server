package org.margin.server.websocket.listeners;

import org.margin.server.notifications.events.NotificationDeliveryEvent;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationWebSocketEventListener {

    private final WebSocketMessageBuilder messageBuilder;
    private final ConnectionManager connectionManager;

    public NotificationWebSocketEventListener(WebSocketMessageBuilder messageBuilder,
                                              ConnectionManager connectionManager) {
        this.messageBuilder = messageBuilder;
        this.connectionManager = connectionManager;
    }

    @EventListener
    public void onNotificationDelivery(NotificationDeliveryEvent event) {
        var notification = event.getNotification();
        if (connectionManager.isUserOnline(notification.getRecipient().getId())) {
            String json = messageBuilder.notification(notification);
            connectionManager.sendToUser(notification.getRecipient().getId(), json);
        }
    }
}