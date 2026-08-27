package org.margin.server.meetings.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.margin.server.meetings.models.MeetingInviteStatus;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meeting_invite")
public class MeetingInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meeting_invite_id")
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 255)
    private String email;

    @Column(name = "invite_token", nullable = false, length = 64, updatable = false)
    private String inviteToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MeetingInviteStatus status = MeetingInviteStatus.PENDING;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
