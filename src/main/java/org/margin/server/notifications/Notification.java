package org.margin.server.notifications;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.margin.server.users.models.User;

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

    @ToString.Exclude
    @ManyToOne
    @JoinColumn(nullable = false, name = "recipient_id")
    private User recipient;

    @ToString.Exclude
    @ManyToOne
    @JoinColumn(nullable = true, name = "sender_id")
    private User sender;

    @Column(nullable = true, name = "margin_id")
    private Long marginId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "type")
    private NotificationType type;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(nullable = false, name = "seen")
    private boolean seen = false;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt = Instant.now();
}