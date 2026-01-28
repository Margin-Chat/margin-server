package org.margin.server.presence;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.notifications.NotificationService;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PresenceService {

    private final NotificationService notificationService;

    public PresenceService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void userConnected(User user) {
        notificationService.notifyUserOnline(user);
        log.info("User {} came online", user.getId());
    }

    public void userDisconnected(User user) {
        notificationService.notifyUserOffline(user);
        log.info("User {} went offline", user.getId());
    }
}