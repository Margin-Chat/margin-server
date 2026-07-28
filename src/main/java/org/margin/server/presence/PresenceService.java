package org.margin.server.presence;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.presence.events.UserConnectedEvent;
import org.margin.server.presence.events.UserDisconnectedEvent;
import org.margin.server.users.models.User;
import org.margin.server.users.api.UserLookup;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
public class PresenceService {

    private final ApplicationEventPublisher eventPublisher;
    private final UserLookup userLookup;
    private final PresenceRegistry presenceRegistry;

    public PresenceService(ApplicationEventPublisher eventPublisher,
                           UserLookup userLookup,
                           PresenceRegistry presenceRegistry) {
        this.eventPublisher = eventPublisher;
        this.userLookup = userLookup;
        this.presenceRegistry = presenceRegistry;
    }

    public boolean isUserOnline(Long userId) {
        return presenceRegistry.isUserOnline(userId);
    }

    public Set<Long> getOnlineUserIds() {
        return presenceRegistry.getOnlineUserIds();
    }

    public void userConnected(User user) {
        eventPublisher.publishEvent(new UserConnectedEvent(user));
        log.debug("User {} came online", user.getId());
    }

    public void userDisconnected(User user) {
        eventPublisher.publishEvent(new UserDisconnectedEvent(user));
        log.debug("User {} went offline", user.getId());
    }

    public void stampLastSeen(Long userId) {
        try {
            userLookup.markLastSeen(userId, Instant.now());
        } catch (Exception e) {
            log.warn("Failed to stamp lastSeenAt for user {}", userId, e);
        }
    }
}