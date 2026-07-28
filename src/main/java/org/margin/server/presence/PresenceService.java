package org.margin.server.presence;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.presence.events.UserConnectedEvent;
import org.margin.server.presence.events.UserDisconnectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
public class PresenceService {

    private final ApplicationEventPublisher eventPublisher;
    private final PresenceRegistry presenceRegistry;

    public PresenceService(ApplicationEventPublisher eventPublisher,
                           PresenceRegistry presenceRegistry) {
        this.eventPublisher = eventPublisher;
        this.presenceRegistry = presenceRegistry;
    }

    public boolean isUserOnline(Long userId) {
        return presenceRegistry.isUserOnline(userId);
    }

    public Set<Long> getOnlineUserIds() {
        return presenceRegistry.getOnlineUserIds();
    }

    public void userConnected(Long userId) {
        eventPublisher.publishEvent(new UserConnectedEvent(userId));
        log.debug("User {} came online", userId);
    }

    public void userDisconnected(Long userId) {
        eventPublisher.publishEvent(new UserDisconnectedEvent(userId));
        log.debug("User {} went offline", userId);
    }
}
