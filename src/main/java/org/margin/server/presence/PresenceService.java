package org.margin.server.presence;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PresenceService {

    private final WebSocketDeliveryService webSocketDeliveryService;

    public PresenceService(WebSocketDeliveryService webSocketDeliveryService) {
        this.webSocketDeliveryService = webSocketDeliveryService;
    }

    public void userConnected(User user) {
        webSocketDeliveryService.notifyUserOnline(user);
        log.info("User {} came online", user.getId());
    }

    public void userDisconnected(User user) {
        webSocketDeliveryService.notifyUserOffline(user);
        log.info("User {} went offline", user.getId());
    }
}