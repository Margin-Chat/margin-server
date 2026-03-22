package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.notifications.Notification;
import org.margin.server.notifications.NotificationType;
import org.margin.server.notifications.repositories.NotificationRepository;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.mockito.ArgumentCaptor;
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
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private WebSocketDeliveryService webSocketDeliveryService;
    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createForUsers_excludesSender() {
        User sender = testUser(1L);
        User member1 = testUser(2L);
        User member2 = testUser(3L);

        notificationService.createForUsers(
                List.of(sender, member1, member2), sender,
                NotificationType.ANNOUNCEMENT, 100L, 10L
        );

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).noneMatch(n -> n.getRecipient().getId().equals(sender.getId()));
    }

    @Test
    void createForUsers_setsCorrectFields() {
        User sender = testUser(1L);
        User member = testUser(2L);

        notificationService.createForUsers(
                List.of(member), sender,
                NotificationType.ANNOUNCEMENT, 100L, 10L
        );

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        Notification saved = captor.getValue().get(0);
        assertThat(saved.getRecipient()).isEqualTo(member);
        assertThat(saved.getSender()).isEqualTo(sender);
        assertThat(saved.getType()).isEqualTo(NotificationType.ANNOUNCEMENT);
        assertThat(saved.getReferenceId()).isEqualTo(100L);
        assertThat(saved.getMarginId()).isEqualTo(10L);
        assertThat(saved.isSeen()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void markNotificationAsSeen() {
        User recipient = testUser(1L);

        Notification n1 = unseenNotification(recipient, 10L);
        Notification n2 = unseenNotification(recipient, 10L);
        Notification n3 = unseenNotification(recipient, 20L);

        when(notificationRepository.findByRecipient_IdAndSeenFalse(recipient.getId()))
                .thenReturn(List.of(n1, n2, n3));

        notificationService.markNotificationAsSeen(recipient.getId(), 10L, request.notificationId);

        assertThat(n1.isSeen()).isTrue();
        assertThat(n2.isSeen()).isTrue();
        assertThat(n3.isSeen()).isFalse();

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void getUnseenCountsPerMargin_groupsCorrectly() {
        User recipient = testUser(1L);

        Notification n1 = unseenNotification(recipient, 10L);
        Notification n2 = unseenNotification(recipient, 10L);
        Notification n3 = unseenNotification(recipient, 20L);

        when(notificationRepository.findByRecipient_IdAndSeenFalse(recipient.getId()))
                .thenReturn(List.of(n1, n2, n3));

        Map<Long, Long> counts = notificationService.getUnseenCountsPerMargin(recipient.getId());

        assertThat(counts).containsEntry(10L, 2L);
        assertThat(counts).containsEntry(20L, 1L);
    }

    @Test
    void getNotificationsForUser_delegatesToRepository() {
        User recipient = testUser(1L);
        List<Notification> expected = List.of(unseenNotification(recipient, 10L));
        when(notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(recipient.getId()))
                .thenReturn(expected);

        List<Notification> result = notificationService.getNotificationsForUser(recipient.getId());

        assertThat(result).isEqualTo(expected);
        verify(notificationRepository).findByRecipient_IdOrderByCreatedAtDesc(recipient.getId());
    }

    private User testUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Notification unseenNotification(User recipient, Long marginId) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setMarginId(marginId);
        n.setSeen(false);
        n.setCreatedAt(LocalDateTime.now());
        return n;
    }
}