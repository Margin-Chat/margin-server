package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.notifications.Notification;
import org.margin.server.notifications.models.dtos.NotificationDTO;
import org.margin.server.shared.notifications.NotificationType;
import org.margin.server.notifications.repositories.NotificationRepository;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.users.api.UserLookup;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserLookup userLookup;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createForUsers_excludesSender() {
        User sender = createUser(1L);
        User member1 = createUser(2L);
        User member2 = createUser(3L);

        notificationService.createForUsers(
                List.of(sender.getId(), member1.getId(), member2.getId()), sender.getId(),
                NotificationType.ANNOUNCEMENT, 100L, 10L
        );

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> saved = captor.getValue();
        assertThat(saved)
                .hasSize(2)
                .noneMatch(n -> n.getRecipientId().equals(sender.getId()));
    }

    @Test
    void createForUsers_setsCorrectFields() {
        User sender = createUser(1L);
        User member = createUser(2L);

        notificationService.createForUsers(
                List.of(member.getId()), sender.getId(),
                NotificationType.ANNOUNCEMENT, 100L, 10L
        );

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        Notification saved = captor.getValue().get(0);
        assertThat(saved.getRecipientId()).isEqualTo(member.getId());
        assertThat(saved.getSenderId()).isEqualTo(sender.getId());
        assertThat(saved.getType()).isEqualTo(NotificationType.ANNOUNCEMENT);
        assertThat(saved.getReferenceId()).isEqualTo(100L);
        assertThat(saved.getMarginId()).isEqualTo(10L);
        assertThat(saved.isSeen()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void markNotificationAsSeen() {
        User recipient = createUser(1L);
        Notification n = unseenNotification(recipient, 10L);

        notificationService.markNotificationAsSeen(n);

        assertThat(n.isSeen()).isTrue();
        verify(notificationRepository).save(n);
    }

    @Test
    void getUnseenCountsPerMargin_groupsCorrectly() {
        User recipient = createUser(1L);

        Notification n1 = unseenNotification(recipient, 10L);
        Notification n2 = unseenNotification(recipient, 10L);
        Notification n3 = unseenNotification(recipient, 20L);

        when(notificationRepository.findByRecipientIdAndSeenFalse(recipient.getId()))
                .thenReturn(List.of(n1, n2, n3));

        Map<Long, Long> counts = notificationService.getUnseenCountsPerMargin(recipient.getId());

        assertThat(counts)
                .containsEntry(10L, 2L)
                .containsEntry(20L, 1L);
    }

    @Test
    void getNotificationsForUser_resolvesSender() {
        User recipient = createUser(1L);
        User sender = createUser(2L);
        Notification notification = unseenNotification(recipient, 10L);
        notification.setType(NotificationType.CONVERSATION_INVITE);
        notification.setSenderId(sender.getId());
        notification.setReferenceId(77L);
        notification.setConversationId(77L);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipient.getId()))
                .thenReturn(List.of(notification));
        when(userLookup.dtosOf(List.of(sender.getId())))
                .thenReturn(List.of(new UserDTO(sender, false)));

        List<NotificationDTO> result = notificationService.getNotificationsForUser(recipient.getId());

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.sender()).isNotNull();
            assertThat(dto.sender().displayName()).isEqualTo(sender.getDisplayName());
            assertThat(dto.type()).isEqualTo(NotificationType.CONVERSATION_INVITE);
            assertThat(dto.referenceId()).isEqualTo(77L);
            assertThat(dto.conversationId()).isEqualTo(77L);
        });
        verify(notificationRepository).findByRecipientIdOrderByCreatedAtDesc(recipient.getId());
    }

    @Test
    void getNotificationsForUser_toleratesMissingSender() {
        User recipient = createUser(1L);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipient.getId()))
                .thenReturn(List.of(unseenNotification(recipient, 10L)));
        when(userLookup.dtosOf(List.of())).thenReturn(List.of());

        List<NotificationDTO> result = notificationService.getNotificationsForUser(recipient.getId());

        assertThat(result).singleElement()
                .satisfies(dto -> assertThat(dto.sender()).isNull());
    }

    @Test
    void createOrCollapseThreadReply_createsNewNotificationPerRecipient() {
        User sender = createUser(1L);
        User author = createUser(2L);
        User replier = createUser(3L);
        when(notificationRepository.findFirstByRecipientIdAndTypeAndConversationIdAndSeenFalse(
                anyLong(), eq(NotificationType.THREAD_REPLY), eq(50L)))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createOrCollapseThreadReply(
                List.of(sender.getId(), author.getId(), replier.getId()), sender.getId(), 200L, 10L, 50L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .noneMatch(n -> n.getRecipientId().equals(sender.getId()))
                .allMatch(n -> n.getType() == NotificationType.THREAD_REPLY)
                .allMatch(n -> n.getConversationId().equals(50L))
                .allMatch(n -> n.getReferenceId().equals(200L));
    }

    @Test
    void createOrCollapseThreadReply_updatesExistingUnseenNotification() {
        User sender = createUser(1L);
        User author = createUser(2L);
        Notification existing = unseenNotification(author, 10L);
        existing.setType(NotificationType.THREAD_REPLY);
        existing.setConversationId(50L);
        existing.setReferenceId(150L);
        when(notificationRepository.findFirstByRecipientIdAndTypeAndConversationIdAndSeenFalse(
                author.getId(), NotificationType.THREAD_REPLY, 50L))
                .thenReturn(Optional.of(existing));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.createOrCollapseThreadReply(
                List.of(author.getId()), sender.getId(), 200L, 10L, 50L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getReferenceId()).isEqualTo(200L);
        assertThat(captor.getValue().getSenderId()).isEqualTo(sender.getId());
    }

    private Notification unseenNotification(User recipient, Long marginId) {
        Notification n = new Notification();
        n.setRecipientId(recipient.getId());
        n.setMarginId(marginId);
        n.setSeen(false);
        n.setCreatedAt(Instant.now());
        return n;
    }
}