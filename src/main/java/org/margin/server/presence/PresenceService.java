package org.margin.server.presence;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class PresenceService {

    private final WebSocketDeliveryService webSocketDeliveryService;
    private final UserRepository userRepository;

    public PresenceService(WebSocketDeliveryService webSocketDeliveryService,
                           UserRepository userRepository) {
        this.webSocketDeliveryService = webSocketDeliveryService;
        this.userRepository = userRepository;
    }

    public void userConnected(User user) {
        webSocketDeliveryService.notifyUserOnline(user);
        log.info("User {} came online", user.getId());
    }

    public void userDisconnected(User user) {
        webSocketDeliveryService.notifyUserOffline(user);
        log.info("User {} went offline", user.getId());
    }

    public void stampLastSeen(Long userId) {
        try {
            userRepository.findById(userId).ifPresent(persisted -> {
                persisted.setLastSeenAt(Instant.now());
                userRepository.save(persisted);
            });
        } catch (Exception e) {
            log.warn("Failed to stamp lastSeenAt for user {}", userId, e);
        }
    }
}