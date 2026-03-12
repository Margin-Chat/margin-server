package org.margin.server.notifications.controllers;

import lombok.RequiredArgsConstructor;
import org.margin.server.notifications.Notification;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.users.models.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/all")
    public List<Notification> getNotifications(@AuthenticationPrincipal User user) {
        return notificationService.getNotificationsForUser(user.getId());
    }

    @GetMapping("/unseen-counts")
    public Map<Long, Long> getUnseenCounts(@AuthenticationPrincipal User user) {
        return notificationService.getUnseenCountsPerMargin(user.getId());
    }

    @PostMapping("/mark-seen")
    public void markSeen(@AuthenticationPrincipal User user,
                         @RequestBody MarkSeenRequest request) {
        notificationService.markSeenForMargin(user.getId(), request.marginId());
    }

    public record MarkSeenRequest(Long marginId) {}
}