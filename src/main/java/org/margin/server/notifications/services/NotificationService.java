package org.margin.server.notifications.services;

import lombok.RequiredArgsConstructor;
import org.margin.server.notifications.Notification;
import org.margin.server.notifications.NotificationType;
import org.margin.server.notifications.repositories.NotificationRepository;
import org.margin.server.users.models.User;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final WebSocketDeliveryService webSocketDeliveryService;

    public NotificationService(NotificationRepository notificationRepository,
                               WebSocketDeliveryService webSocketDeliveryService) {
        this.notificationRepository = notificationRepository;
        this.webSocketDeliveryService = webSocketDeliveryService;
    }

    @Transactional
    public void createForMarginMembers(List<User> members, User sender, NotificationType type,
                                       Long referenceId, Long marginId) {
        List<Notification> notifications = members.stream()
                .filter(member -> !member.getId().equals(sender.getId()))
                .map(member -> {
                    Notification n = new Notification();
                    n.setRecipient(member);
                    n.setSender(sender);
                    n.setType(type);
                    n.setReferenceId(referenceId);
                    n.setMarginId(marginId);
                    n.setSeen(false);
                    n.setCreatedAt(LocalDateTime.now());
                    return n;
                })
                .toList();

        notificationRepository.saveAll(notifications);

        notifications.forEach(webSocketDeliveryService::notifyNotification);
    }

    @Transactional
    public void markSeenForMargin(Long recipientId, Long marginId) {
        List<Notification> unseen = notificationRepository.findByRecipient_IdAndSeenFalse(recipientId)
                .stream()
                .filter(n -> n.getMarginId().equals(marginId))
                .toList();

        unseen.forEach(n -> n.setSeen(true));
        notificationRepository.saveAll(unseen);
    }

    public List<Notification> getNotificationsForUser(Long recipientId) {
        return notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(recipientId);
    }

    public Map<Long, Long> getUnseenCountsPerMargin(Long recipientId) {
        return notificationRepository.findByRecipient_IdAndSeenFalse(recipientId)
                .stream()
                .collect(Collectors.groupingBy(Notification::getMarginId, Collectors.counting()));
    }
}