package org.margin.server.meetings.repositories;

import org.margin.server.meetings.entities.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    Optional<Meeting> findByCode(String code);

    @Query("""
            SELECT m FROM Meeting m
            WHERE m.id IN (
                SELECT p.meetingId FROM MeetingParticipant p WHERE p.userId = :userId
                UNION
                SELECT i.meetingId FROM MeetingInvite i WHERE i.userId = :userId
            )
            ORDER BY COALESCE(m.scheduledAt, m.createdAt) DESC
            """)
    List<Meeting> findForUser(@Param("userId") Long userId);

    @Query("""
            SELECT m FROM Meeting m
            WHERE m.status = org.margin.server.meetings.models.MeetingStatus.SCHEDULED
              AND m.reminderSentAt IS NULL
              AND m.scheduledAt IS NOT NULL
              AND m.scheduledAt <= :until
              AND m.scheduledAt > :now
            """)
    List<Meeting> findDueReminders(@Param("now") Instant now, @Param("until") Instant until);

    @Query("""
            SELECT m FROM Meeting m
            WHERE m.status = org.margin.server.meetings.models.MeetingStatus.LIVE
              AND m.startedNoticeSentAt IS NULL
              AND m.scheduledAt IS NOT NULL
            """)
    List<Meeting> findMeetingsNeedingStartedNotice();
}
