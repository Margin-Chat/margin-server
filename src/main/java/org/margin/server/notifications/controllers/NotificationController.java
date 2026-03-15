package org.margin.server.notifications.controllers;

import org.margin.server.notifications.Notification;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.users.models.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/all")
    public List<Notification> getNotifications(@AuthenticationPrincipal User user) {
        return notificationService.getNotificationsForUser(user.getId());
    }

    @GetMapping("/unseen_counts")
    public Map<Long, Long> getUnseenCounts(@AuthenticationPrincipal User user) {
        return notificationService.getUnseenCountsPerMargin(user.getId());
    }

    @PostMapping("/mark_seen")
    public void markSeen(@AuthenticationPrincipal User user,
                         @RequestBody MarkSeenRequest request) {
        notificationService.markSeenForMargin(user.getId(), request.marginId());
    }

    @PostMapping("/mark_seen_by_reference")
    public void markSeenByReference(@AuthenticationPrincipal User user,
                                    @RequestBody MarkSeenByReferenceRequest request) {
        notificationService.markSeenByReference(user.getId(), request.referenceId());
    }

    public record MarkSeenRequest(Long marginId) {
    }

    public record MarkSeenByReferenceRequest(Long referenceId) {
    }

}