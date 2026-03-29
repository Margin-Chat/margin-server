package org.margin.server.social.calls.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.margin.server.users.models.User;

import java.time.Instant;

@Entity
@Table(name = "calls")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Call {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caller_id", nullable = false)
    private User caller;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallType type;

    @Column(name = "offered_at", nullable = false)
    private Instant offeredAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    public Call(User caller,
                User receiverId,
                CallStatus status,
                CallType type) {
        this.caller = caller;
        this.receiver = receiverId;
        this.status = status;
        this.type = type;
    }

    @PrePersist
    protected void onCreate() {
        offeredAt = Instant.now();
        if (status == null) {
            status = CallStatus.OFFERED;
        }
    }
}
