package org.margin.server.websocket.processors;

import org.margin.server.notifications.NotificationService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;

@Component
public class ScreenShareStoppedProcessor implements WebSocketMessageProcessor<Object> {

    private final NotificationService notificationService;

    public ScreenShareStoppedProcessor(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SCREEN_SHARE_STOPPED;
    }

    @Override
    public void process(User user, WebSocketMessageIn<Object> message) {
        notificationService.notifyScreenShareStopped(message.getRecipientId());
    }
}