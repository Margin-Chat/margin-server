package org.margin.server.websocket.processors;

import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;

@Component
public class ScreenShareStartedProcessor implements WebSocketMessageProcessor<String> {

    private final WebSocketDeliveryService webSocketDeliveryService;

    public ScreenShareStartedProcessor(WebSocketDeliveryService webSocketDeliveryService) {
        this.webSocketDeliveryService = webSocketDeliveryService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SCREEN_SHARE_STARTED;
    }

    @Override
    public void process(User user, WebSocketMessageIn<String> message) {
        webSocketDeliveryService.notifyScreenShareStarted(message.getRecipientId());
    }
}