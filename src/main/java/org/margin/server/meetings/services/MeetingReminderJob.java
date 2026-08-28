package org.margin.server.meetings.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.meetings.entities.Meeting;
import org.margin.server.meetings.entities.MeetingInvite;
import org.margin.server.meetings.repositories.MeetingInviteRepository;
import org.margin.server.meetings.repositories.MeetingParticipantRepository;
import org.margin.server.meetings.repositories.MeetingRepository;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.shared.notifications.NotificationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class MeetingReminderJob {

    private static final Duration LEAD_TIME = Duration.ofMinutes(10);

    private final MeetingRepository meetingRepository;
    private final MeetingInviteRepository inviteRepository;
    private final MeetingParticipantRepository participantRepository;
    private final NotificationService notifications;

    @Value("${margin.meetings.reminders-enabled:true}")
    private boolean enabled;

    public MeetingReminderJob(MeetingRepository meetingRepository,
                              MeetingInviteRepository inviteRepository,
                              MeetingParticipantRepository participantRepository,
                              NotificationService notifications) {
        this.meetingRepository = meetingRepository;
        this.inviteRepository = inviteRepository;
        this.participantRepository = participantRepository;
        this.notifications = notifications;
    }

    @Scheduled(fixedDelayString = "${scheduling.meeting-reminder-delay-ms:60000}")
    public void scheduledSweep() {
        if (!enabled) {
            return;
        }
        sendDueReminders(Instant.now());
        sendStartedNotices();
    }

    @Transactional
    public int sendDueReminders(Instant now) {
        List<Meeting> due = meetingRepository.findDueReminders(now, now.plus(LEAD_TIME));

        for (Meeting meeting : due) {
            notify(meeting, NotificationType.MEETING_REMINDER);
            meeting.setReminderSentAt(now);
            meetingRepository.save(meeting);
        }

        return due.size();
    }

    @Transactional
    public int sendStartedNotices() {
        List<Meeting> started = meetingRepository.findMeetingsNeedingStartedNotice();

        for (Meeting meeting : started) {
            notify(meeting, NotificationType.MEETING_STARTED);
            meeting.setStartedNoticeSentAt(Instant.now());
            meetingRepository.save(meeting);
        }

        return started.size();
    }

    private void notify(Meeting meeting, NotificationType type) {
        List<Long> recipients = recipientsFor(meeting);
        if (recipients.isEmpty()) {
            return;
        }
        notifications.createForUsers(recipients, meeting.getHostUserId(), type,
                meeting.getId(), meeting.getMarginId());
    }

    private List<Long> recipientsFor(Meeting meeting) {
        List<Long> ids = new ArrayList<>();

        inviteRepository.findByMeetingId(meeting.getId()).stream()
                .map(MeetingInvite::getUserId)
                .filter(Objects::nonNull)
                .forEach(ids::add);

        participantRepository.findByMeetingId(meeting.getId()).stream()
                .filter(p -> !p.isGuest())
                .map(p -> p.getUserId())
                .filter(Objects::nonNull)
                .forEach(ids::add);

        return ids.stream().distinct().filter(id -> !id.equals(meeting.getHostUserId())).toList();
    }
}
