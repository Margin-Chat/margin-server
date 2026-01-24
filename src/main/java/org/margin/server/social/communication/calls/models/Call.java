package org.margin.server.social.communication.calls.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "calls")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Call {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caller_id", nullable = false)
    private Long callerId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallType type;

    @Column(columnDefinition = "TEXT")
    private String sdp;

    @Column(name = "offered_at", nullable = false)
    private LocalDateTime offeredAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    public Call(Long callerId, Long receiverId, CallStatus status, CallType type, String sdp) {
        this.callerId = callerId;
        this.receiverId = receiverId;
        this.status = status;
        this.type = type;
        this.sdp = sdp;
    }

    @PrePersist
    protected void onCreate() {
        offeredAt = LocalDateTime.now();
        if (status == null) {
            status = CallStatus.OFFERED;
        }
    }
}
