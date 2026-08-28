package org.margin.server.meetings.repositories;

import org.margin.server.meetings.entities.MeetingInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingInviteRepository extends JpaRepository<MeetingInvite, Long> {

    List<MeetingInvite> findByMeetingId(Long meetingId);

    java.util.Optional<MeetingInvite> findByInviteToken(String inviteToken);

    int countByMeetingId(Long meetingId);
}
