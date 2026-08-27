package org.margin.server.meetings.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.margin.server.meetings.models.MeetingStatus;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meeting")
public class Meeting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meeting_id")
    private Long id;

    @Column(nullable = false, length = 32, updatable = false)
    private String code;

    @Column(name = "host_user_id", nullable = false)
    private Long hostUserId;

    @Column(name = "margin_id", nullable = false, updatable = false)
    private Long marginId;

    @Column(length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MeetingStatus status;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "organizer_timezone", length = 64)
    private String organizerTimezone;

    @Column(name = "require_admission", nullable = false)
    private boolean requireAdmission = true;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Column(name = "max_video_height")
    private Integer maxVideoHeight;

    @Column(name = "ics_sequence", nullable = false)
    private int icsSequence = 0;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "started_notice_sent_at")
    private Instant startedNoticeSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isJoinable() {
        return status == MeetingStatus.SCHEDULED || status == MeetingStatus.LIVE;
    }
}
