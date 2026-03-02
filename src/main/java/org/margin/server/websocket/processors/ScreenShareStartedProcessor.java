package org.margin.server.websocket.processors;

import org.margin.server.notifications.NotificationService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;

@Component
public class ScreenShareStartedProcessor implements WebSocketMessageProcessor<String> {

    private final NotificationService notificationService;

    public ScreenShareStartedProcessor(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SCREEN_SHARE_STARTED;
    }

    @Override
    public void process(User user, WebSocketMessageIn<String> message) {
        notificationService.notifyScreenShareStarted(message.getRecipientId());
    }
}