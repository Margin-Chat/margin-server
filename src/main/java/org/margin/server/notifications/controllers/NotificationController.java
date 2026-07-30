package org.margin.server.notifications.controllers;

import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.notifications.Notification;
import org.margin.server.notifications.services.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/all")
    public List<Notification> getNotifications(@AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.getNotificationsForUser(user.id());
    }

    @GetMapping("/unseen_counts")
    public Map<Long, Long> getUnseenCounts(@AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.getUnseenCountsPerMargin(user.id());
    }

    @PostMapping("/mark_seen")
    public void markSeen(@AuthenticationPrincipal AuthenticatedUser user,
                         @RequestBody Long notificationId) {
        Notification notification = notificationService.getNotification(notificationId);
        if (!notification.getRecipientId().equals(user.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        notificationService.markNotificationAsSeen(notification);
    }
}