package org.margin.server.notifications.repositories;

import org.margin.server.notifications.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipient_IdOrderByCreatedAtDesc(Long recipientId);
    List<Notification> findByRecipient_IdAndSeenFalse(Long recipientId);
    long countByRecipient_IdAndMarginIdAndSeenFalse(Long recipientId, Long marginId);
}