package org.margin.server.meetings.repositories;

import org.margin.server.meetings.entities.MeetingParticipant;
import org.margin.server.meetings.models.ParticipantState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    Optional<MeetingParticipant> findByMeetingIdAndUserId(Long meetingId, Long userId);

    List<MeetingParticipant> findByMeetingId(Long meetingId);

    List<MeetingParticipant> findByMeetingIdAndState(Long meetingId, ParticipantState state);
}
