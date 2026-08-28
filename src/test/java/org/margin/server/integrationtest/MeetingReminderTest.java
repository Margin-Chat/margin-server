package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.SubscriptionTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.meetings.entities.Meeting;
import org.margin.server.meetings.models.dtos.MeetingSummaryDTO;
import org.margin.server.meetings.models.dtos.RescheduleMeetingRequest;
import org.margin.server.meetings.models.dtos.ScheduleMeetingRequest;
import org.margin.server.meetings.repositories.MeetingRepository;
import org.margin.server.meetings.services.MeetingReminderJob;
import org.margin.server.meetings.services.MeetingSchedulingService;
import org.margin.server.notifications.repositories.NotificationRepository;
import org.margin.server.shared.notifications.NotificationType;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeetingReminderTest extends MarginTestRunner {

    @Autowired
    private MeetingSchedulingService schedulingService;
    @Autowired
    private MeetingReminderJob reminderJob;
    @Autowired
    private MeetingRepository meetingRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private MarginService marginService;

    private User host;
    private User invitee;
    private Margin margin;

    private void seed() {
        host = UserTestUtils.createUser("alice", "alice@margin.chat");
        invitee = UserTestUtils.createUser("bob", "bob@margin.chat");
        margin = MarginTestUtils.createMargin("Acme", host);
        SubscriptionTestUtils.setActiveSubscription(margin, "c", "s", SubscriptionTier.SMALL);
        marginService.addUserToMargin(margin.getId(), invitee.getId(), MarginRole.MEMBER, host.getId(), false);
    }

    private MeetingSummaryDTO scheduleAt(Instant when) {
        return schedulingService.schedule(host.getId(), new ScheduleMeetingRequest(
                margin.getId(), "Sync", when, 30, null, null,
                List.of(new ScheduleMeetingRequest.InviteeRequest(invitee.getId(), null))));
    }

    private long remindersFor(Long meetingId) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.MEETING_REMINDER)
                .filter(n -> meetingId.equals(n.getReferenceId()))
                .count();
    }

    @Test
    void aMeetingStartingSoonRemindsItsInvitees() {
        seed();
        MeetingSummaryDTO meeting = scheduleAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        int sent = reminderJob.sendDueReminders(Instant.now());

        assertTrue(sent >= 1);
        assertEquals(1, remindersFor(meeting.id()));
    }

    @Test
    void aMeetingFurtherOutIsLeftAlone() {
        seed();
        MeetingSummaryDTO meeting = scheduleAt(Instant.now().plus(3, ChronoUnit.HOURS));

        reminderJob.sendDueReminders(Instant.now());

        assertEquals(0, remindersFor(meeting.id()));
    }

    @Test
    void runningTheSweepTwiceDoesNotRemindTwice() {
        seed();
        MeetingSummaryDTO meeting = scheduleAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        reminderJob.sendDueReminders(Instant.now());
        reminderJob.sendDueReminders(Instant.now());
        reminderJob.sendDueReminders(Instant.now());

        assertEquals(1, remindersFor(meeting.id()),
                "the stamp is what stops a crashed sweep re-notifying everyone on the next tick");
    }

    @Test
    void theStampIsWrittenSoARestartDoesNotResend() {
        seed();
        MeetingSummaryDTO meeting = scheduleAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        reminderJob.sendDueReminders(Instant.now());

        Meeting stored = meetingRepository.findByCode(meeting.code()).orElseThrow();
        assertNotNull(stored.getReminderSentAt());
    }

    @Test
    void movingAMeetingLetsTheReminderFireAgainForTheNewTime() {
        seed();
        MeetingSummaryDTO meeting = scheduleAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        reminderJob.sendDueReminders(Instant.now());
        assertEquals(1, remindersFor(meeting.id()));

        schedulingService.reschedule(meeting.code(), host.getId(),
                new RescheduleMeetingRequest(Instant.now().plus(6, ChronoUnit.MINUTES), 30, null));
        reminderJob.sendDueReminders(Instant.now());

        assertEquals(2, remindersFor(meeting.id()), "the new time deserves its own reminder");
    }

    @Test
    void aMeetingAlreadyUnderwayIsNotRemindedAbout() {
        seed();
        MeetingSummaryDTO meeting = scheduleAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        Meeting stored = meetingRepository.findByCode(meeting.code()).orElseThrow();
        stored.setScheduledAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        meetingRepository.save(stored);

        reminderJob.sendDueReminders(Instant.now());

        assertEquals(0, remindersFor(meeting.id()));
    }

    @Test
    void theHostIsNotRemindedAboutTheirOwnMeeting() {
        seed();
        MeetingSummaryDTO meeting = scheduleAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        reminderJob.sendDueReminders(Instant.now());

        assertTrue(notificationRepository.findAll().stream()
                        .filter(n -> n.getType() == NotificationType.MEETING_REMINDER)
                        .filter(n -> meeting.id().equals(n.getReferenceId()))
                        .noneMatch(n -> n.getRecipientId().equals(host.getId())),
                "the person who booked it does not need telling");
    }
}
