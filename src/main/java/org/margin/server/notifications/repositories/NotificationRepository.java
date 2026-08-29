package org.margin.server.notifications.repositories;

import org.margin.server.notifications.Notification;
import org.margin.server.shared.notifications.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<Notification> findByRecipientIdAndSeenFalse(Long recipientId);

    long countByRecipientIdAndMarginIdAndSeenFalse(Long recipientId, Long marginId);

    Optional<Notification> findFirstByRecipientIdAndTypeAndConversationIdAndSeenFalse(
            Long recipientId, NotificationType type, Long conversationId);

    @Modifying
    @Query("UPDATE Notification n SET n.seen = true WHERE n.recipientId = :userId AND n.referenceId = :referenceId")
    void markSeenByReference(Long userId, Long referenceId);
}