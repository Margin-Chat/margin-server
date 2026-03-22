package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.notifications.Notification;
import org.margin.server.notifications.NotificationType;
import org.margin.server.notifications.controllers.NotificationController;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.users.models.User;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    void getNotifications_returnsUserNotifications() {
        User user = testUser(1L);
        List<Notification> expected = List.of(testNotification(user, 10L));
        when(notificationService.getNotificationsForUser(user.getId())).thenReturn(expected);

        List<Notification> result = notificationController.getNotifications(user);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getUnseenCounts_returnsCountMap() {
        User user = testUser(1L);
        Map<Long, Long> expected = Map.of(10L, 3L, 20L, 1L);
        when(notificationService.getUnseenCountsPerMargin(user.getId())).thenReturn(expected);

        Map<Long, Long> result = notificationController.getUnseenCounts(user);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void markSeen_delegatesToService() {
        User user = testUser(1L);
        NotificationController.MarkSeenRequest request = new NotificationController.MarkSeenRequest(10L);

        notificationController.markSeen(user, request);

        verify(notificationService).markNotificationAsSeen(user.getId(), 10L, request.notificationId);
    }

    private User testUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Notification testNotification(User recipient, Long marginId) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setMarginId(marginId);
        n.setType(NotificationType.ANNOUNCEMENT);
        n.setSeen(false);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }
}
