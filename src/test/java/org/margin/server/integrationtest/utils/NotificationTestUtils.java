package org.margin.server.integrationtest.utils;

import org.margin.server.notifications.Notification;
import org.margin.server.shared.notifications.NotificationType;
import org.margin.server.notifications.repositories.NotificationRepository;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationTestUtils {
    private static NotificationRepository notificationRepository;

    @Autowired
    public NotificationTestUtils(NotificationRepository notificationRepository) {
        NotificationTestUtils.notificationRepository = notificationRepository;
    }

    public static List<Notification> getForUser(User user) {
        return notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(user.getId());
    }

    public static boolean hasNotification(User user, NotificationType type) {
        return getForUser(user).stream().anyMatch(n -> n.getType() == type);
    }
}
