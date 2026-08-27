package org.margin.server.meetings.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.margin.server.meetings.models.MeetingRole;
import org.margin.server.meetings.models.ParticipantState;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meeting_participant")
public class MeetingParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meeting_participant_id")
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "is_guest", nullable = false)
    private boolean guest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MeetingRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ParticipantState state;

    @Column(name = "knocked_at")
    private Instant knockedAt;

    @Column(name = "admitted_at")
    private Instant admittedAt;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "admitted_by")
    private Long admittedBy;
}
