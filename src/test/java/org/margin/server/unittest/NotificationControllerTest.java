package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import static org.margin.server.unittest.utils.UserTestUtils.principalOf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.notifications.Notification;
import org.margin.server.notifications.models.dtos.NotificationDTO;
import org.margin.server.shared.notifications.NotificationType;
import org.margin.server.notifications.controllers.NotificationController;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.users.models.User;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.margin.server.unittest.utils.UserTestUtils.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    void getNotifications_returnsUserNotifications() {
        User user = createUser(1L);
        List<NotificationDTO> expected = List.of(new NotificationDTO(
                1L, NotificationType.ANNOUNCEMENT, 100L, 10L, null, null, false, Instant.now()));
        when(notificationService.getNotificationsForUser(user.getId())).thenReturn(expected);

        List<NotificationDTO> result = notificationController.getNotifications(principalOf(user));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getUnseenCounts_returnsCountMap() {
        User user = createUser(1L);
        Map<Long, Long> expected = Map.of(10L, 3L, 20L, 1L);
        when(notificationService.getUnseenCountsPerMargin(user.getId())).thenReturn(expected);

        Map<Long, Long> result = notificationController.getUnseenCounts(principalOf(user));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void markSeen_delegatesToService() {
        User user = createUser(1L);
        Notification notification = testNotification(user, 10L);
        when(notificationService.getNotification(99L)).thenReturn(notification);

        notificationController.markSeen(principalOf(user), 99L);

        verify(notificationService).markNotificationAsSeen(notification);
    }

    @Test
    void markSeen_throwsForbidden_whenNotificationBelongsToOtherUser() {
        User requester = createUser(1L);
        User owner = createUser(2L);
        Notification notification = testNotification(owner, 10L);
        when(notificationService.getNotification(99L)).thenReturn(notification);

        assertThatThrownBy(() -> notificationController.markSeen(principalOf(requester), 99L))
                .isInstanceOf(ResponseStatusException.class);
    }

    private Notification testNotification(User recipient, Long marginId) {
        Notification n = new Notification();
        n.setRecipientId(recipient.getId());
        n.setMarginId(marginId);
        n.setType(NotificationType.ANNOUNCEMENT);
        n.setSeen(false);
        n.setCreatedAt(Instant.now());
        return n;
    }
}