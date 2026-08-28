package org.margin.server.notifications.services;

import org.margin.server.notifications.Notification;
import org.margin.server.notifications.models.dtos.NotificationDTO;
import org.margin.server.shared.notifications.NotificationType;
import org.margin.server.notifications.repositories.NotificationRepository;
import org.margin.server.notifications.events.NotificationDeliveryEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.margin.server.users.api.UserLookup;
import org.margin.server.users.models.dtos.UserDTO;

import java.util.stream.Collectors;

@NamedInterface("api")
@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserLookup userLookup;

    public NotificationService(NotificationRepository notificationRepository,
                               ApplicationEventPublisher eventPublisher,
                               UserLookup userLookup) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
        this.userLookup = userLookup;
    }

    @Transactional
    public void createForUsers(List<Long> recipientIds, Long senderId, NotificationType type,
                               Long referenceId, Long marginId) {
        createForUsers(recipientIds, senderId, type, referenceId, marginId, null);
    }

    @Transactional
    public void createForUsers(List<Long> recipientIds, Long senderId, NotificationType type,
                               Long referenceId, Long marginId, Long conversationId) {
        List<Notification> notifications = recipientIds.stream()
                .filter(recipientId -> senderId == null || !recipientId.equals(senderId))
                .filter(recipientId -> !userLookup.isGuest(recipientId))
                .map(recipientId -> {
                    Notification n = new Notification();
                    n.setRecipientId(recipientId);
                    n.setSenderId(senderId);
                    n.setType(type);
                    n.setReferenceId(referenceId);
                    n.setMarginId(marginId);
                    n.setConversationId(conversationId);
                    n.setSeen(false);
                    n.setCreatedAt(Instant.now());
                    return n;
                })
                .toList();

        notificationRepository.saveAll(notifications);

        notifications.forEach(n -> eventPublisher.publishEvent(new NotificationDeliveryEvent(n)));
    }

    @Transactional
    public void createOrCollapseThreadReply(List<Long> recipientIds, Long senderId, Long referenceId,
                                            Long marginId, Long threadConversationId) {
        for (Long recipientId : recipientIds) {
            if (senderId != null && recipientId.equals(senderId)) {
                continue;
            }
            Notification notification = notificationRepository
                    .findFirstByRecipientIdAndTypeAndConversationIdAndSeenFalse(
                            recipientId, NotificationType.THREAD_REPLY, threadConversationId)
                    .orElseGet(() -> {
                        Notification n = new Notification();
                        n.setRecipientId(recipientId);
                        n.setType(NotificationType.THREAD_REPLY);
                        n.setConversationId(threadConversationId);
                        n.setSeen(false);
                        return n;
                    });
            notification.setSenderId(senderId);
            notification.setReferenceId(referenceId);
            notification.setMarginId(marginId);
            notification.setCreatedAt(Instant.now());
            notification = notificationRepository.save(notification);
            eventPublisher.publishEvent(new NotificationDeliveryEvent(notification));
        }
    }

    @Transactional
    public void markNotificationAsSeen(Notification notification) {
        notification.setSeen(true);
        notificationRepository.save(notification);
    }

    public List<NotificationDTO> getNotificationsForUser(Long recipientId) {
        List<Notification> notifications =
                notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);

        Map<Long, UserDTO> senders = userLookup.dtosOf(notifications.stream()
                        .map(Notification::getSenderId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(UserDTO::id, Function.identity()));

        return notifications.stream()
                .map(n -> new NotificationDTO(
                        n.getNotificationId(),
                        n.getType(),
                        n.getReferenceId(),
                        n.getMarginId(),
                        n.getConversationId(),
                        n.getSenderId() == null ? null : senders.get(n.getSenderId()),
                        n.isSeen(),
                        n.getCreatedAt()))
                .toList();
    }

    public Map<Long, Long> getUnseenCountsPerMargin(Long recipientId) {
        return notificationRepository.findByRecipientIdAndSeenFalse(recipientId)
                .stream()
                .filter(n -> n.getMarginId() != null)
                .collect(Collectors.groupingBy(Notification::getMarginId, Collectors.counting()));
    }

    @Transactional
    public void markSeenByReference(Long userId, Long referenceId) {
        notificationRepository.markSeenByReference(userId, referenceId);
    }

    public Notification getNotification(Long notificationId) {
        return notificationRepository.findById(notificationId).orElseThrow();
    }
}