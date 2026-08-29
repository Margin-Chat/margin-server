package org.margin.server.websocket.listeners;

import org.margin.server.subscriptions.events.SubscriptionUpdatedEvent;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionWebSocketEventListener {

    private final WebSocketMessageBuilder messageBuilder;
    private final ConnectionManager connectionManager;

    public SubscriptionWebSocketEventListener(WebSocketMessageBuilder messageBuilder,
                                              ConnectionManager connectionManager) {
        this.messageBuilder = messageBuilder;
        this.connectionManager = connectionManager;
    }

    @EventListener
    public void onSubscriptionUpdated(SubscriptionUpdatedEvent event) {
        String json = messageBuilder.buildMessage(
                WebSocketMessageType.SUBSCRIPTION_UPDATED, event.recipientId(), event.subscription());
        connectionManager.sendToUser(event.recipientId(), json);
    }
}
