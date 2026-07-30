package org.margin.server.notifications;

import org.margin.server.shared.notifications.NotificationType;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @Column(nullable = false, name = "recipient_id")
    private Long recipientId;

    @Column(name = "sender_id")
    private Long senderId;

    @Column(nullable = true, name = "margin_id")
    private Long marginId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "type")
    private NotificationType type;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(nullable = false, name = "seen")
    private boolean seen = false;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt = Instant.now();
}