package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.SubscriptionTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.meetings.models.MeetingStatus;
import org.margin.server.meetings.models.dtos.MeetingInviteDTO;
import org.margin.server.meetings.models.dtos.MeetingSummaryDTO;
import org.margin.server.meetings.models.dtos.RescheduleMeetingRequest;
import org.margin.server.meetings.models.dtos.ScheduleMeetingRequest;
import org.margin.server.meetings.repositories.MeetingRepository;
import org.margin.server.meetings.services.MeetingSchedulingService;
import org.margin.server.meetings.services.MeetingService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.subscriptions.exceptions.SubscriptionLimitExceededException;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeetingSchedulingTest extends MarginTestRunner {

    @Autowired
    private MeetingSchedulingService schedulingService;
    @Autowired
    private MeetingService meetingService;
    @Autowired
    private MeetingRepository meetingRepository;
    @Autowired
    private MarginService marginService;

    private User host;
    private User colleague;
    private Margin margin;

    private void seed() {
        host = UserTestUtils.createUser("alice", "alice@margin.chat");
        colleague = UserTestUtils.createUser("bob", "bob@margin.chat");
        margin = MarginTestUtils.createMargin("Acme", host);
        SubscriptionTestUtils.setActiveSubscription(margin, "c", "s", SubscriptionTier.SMALL);
        marginService.addUserToMargin(margin.getId(), colleague.getId(), MarginRole.MEMBER, host.getId(), false);
    }

    private Instant soon() {
        return Instant.now().plus(2, ChronoUnit.HOURS);
    }

    private MeetingSummaryDTO scheduleBasic() {
        return schedulingService.schedule(host.getId(), new ScheduleMeetingRequest(
                margin.getId(), "Weekly sync", soon(), 30, "Europe/Lisbon", null, null));
    }

    @Test
    void schedulingCreatesAMeetingThatHasNotStarted() {
        seed();

        MeetingSummaryDTO meeting = scheduleBasic();

        assertEquals(MeetingStatus.SCHEDULED, meeting.status());
        assertNotNull(meeting.scheduledAt());
        assertEquals(30, meeting.durationMinutes());
        assertEquals("Europe/Lisbon", meeting.organizerTimezone());
        assertNull(meeting.startedAt());
    }

    @Test
    void aMeetingInThePastIsRejected() {
        seed();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> schedulingService.schedule(host.getId(), new ScheduleMeetingRequest(
                        margin.getId(), "Late", Instant.now().minus(1, ChronoUnit.HOURS),
                        30, null, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void anUnknownTimezoneIsRejected() {
        seed();

        assertThrows(ResponseStatusException.class,
                () -> schedulingService.schedule(host.getId(), new ScheduleMeetingRequest(
                        margin.getId(), "Sync", soon(), 30, "Mars/Olympus", null, null)));
    }

    @Test
    void aFreeMarginCannotScheduleEither() {
        seed();
        User frank = UserTestUtils.createUser("frank", "frank@margin.chat");
        Margin free = MarginTestUtils.createMargin("Frugal", frank);

        assertThrows(SubscriptionLimitExceededException.class,
                () -> schedulingService.schedule(frank.getId(), new ScheduleMeetingRequest(
                        free.getId(), "Sync", soon(), 30, null, null, null)));
    }

    @Test
    void inviteesAreRecordedSeparatelyFromAttendance() {
        seed();

        MeetingSummaryDTO meeting = schedulingService.schedule(host.getId(), new ScheduleMeetingRequest(
                margin.getId(), "Sync", soon(), 30, null, null,
                List.of(new ScheduleMeetingRequest.InviteeRequest(colleague.getId(), null),
                        new ScheduleMeetingRequest.InviteeRequest(null, "outsider@example.com"))));

        List<MeetingInviteDTO> invitees = schedulingService.invitees(meeting.code(), host.getId());
        assertEquals(2, invitees.size());
        assertEquals(2, meeting.inviteeCount());
        assertTrue(invitees.stream().anyMatch(i -> "outsider@example.com".equals(i.email())));
        assertTrue(invitees.stream().anyMatch(i -> colleague.getId().equals(i.userId())));
    }

    @Test
    void anInviteeNeedsEitherAUserOrAnEmail() {
        seed();

        assertThrows(ResponseStatusException.class,
                () -> schedulingService.schedule(host.getId(), new ScheduleMeetingRequest(
                        margin.getId(), "Sync", soon(), 30, null, null,
                        List.of(new ScheduleMeetingRequest.InviteeRequest(null, null)))));
    }

    @Test
    void aGuestAddressCannotBeInvited() {
        seed();

        assertThrows(ResponseStatusException.class,
                () -> schedulingService.schedule(host.getId(), new ScheduleMeetingRequest(
                        margin.getId(), "Sync", soon(), 30, null, null,
                        List.of(new ScheduleMeetingRequest.InviteeRequest(
                                null, "guest_abc@guests.margin.invalid")))));
    }

    @Test
    void reschedulingMovesTheTimeAndMarksTheCalendarEntryAsAnUpdate() {
        seed();
        MeetingSummaryDTO meeting = scheduleBasic();
        int sequenceBefore = meetingRepository.findByCode(meeting.code()).orElseThrow().getIcsSequence();
        Instant moved = Instant.now().plus(5, ChronoUnit.HOURS);

        schedulingService.reschedule(meeting.code(), host.getId(),
                new RescheduleMeetingRequest(moved, 45, null));

        var stored = meetingRepository.findByCode(meeting.code()).orElseThrow();
        assertEquals(45, stored.getDurationMinutes());
        assertEquals(sequenceBefore + 1, stored.getIcsSequence(),
                "calendar clients treat an unchanged sequence as a duplicate rather than an edit");
        assertNull(stored.getReminderSentAt(), "the reminder must fire again for the new time");
    }

    @Test
    void onlyTheHostCanReschedule() {
        seed();
        MeetingSummaryDTO meeting = scheduleBasic();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> schedulingService.reschedule(meeting.code(), colleague.getId(),
                        new RescheduleMeetingRequest(soon(), 30, null)));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void cancellingMarksTheMeetingCancelled() {
        seed();
        MeetingSummaryDTO meeting = scheduleBasic();

        MeetingSummaryDTO cancelled = schedulingService.cancel(meeting.code(), host.getId());

        assertEquals(MeetingStatus.CANCELLED, cancelled.status());
        assertNotNull(meetingRepository.findByCode(meeting.code()).orElseThrow().getCancelledAt());
    }

    @Test
    void aCancelledMeetingCannotBeJoined() {
        seed();
        MeetingSummaryDTO meeting = scheduleBasic();
        schedulingService.cancel(meeting.code(), host.getId());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> meetingService.join(meeting.code(), host.getId()));

        assertEquals(HttpStatus.GONE, e.getStatusCode());
    }

    @Test
    void aLiveMeetingIsEndedRatherThanCancelled() {
        seed();
        var live = meetingService.createInstantMeeting(host.getId(),
                new org.margin.server.meetings.models.dtos.CreateMeetingRequest(
                        margin.getId(), "Now", null));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> schedulingService.cancel(live.code(), host.getId()));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void aScheduledMeetingHasNotStartedUntilSomeoneArrives() {
        seed();
        MeetingSummaryDTO meeting = scheduleBasic();

        var stored = meetingRepository.findByCode(meeting.code()).orElseThrow();
        assertEquals(MeetingStatus.SCHEDULED, stored.getStatus());
        assertNull(stored.getStartedAt(), "the clock reaching the start time does not begin a meeting");
    }

    @Test
    void joiningWhileTheSfuIsUnreachableSaysSoRatherThanFailingOpaquely() {
        seed();
        MeetingSummaryDTO meeting = scheduleBasic();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> meetingService.join(meeting.code(), host.getId()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatusCode());
        assertEquals(MeetingStatus.SCHEDULED,
                meetingRepository.findByCode(meeting.code()).orElseThrow().getStatus(),
                "a failed join must not leave the meeting marked as started");
    }

    @Test
    void myMeetingsCoversBothHostingAndBeingInvited() {
        seed();
        MeetingSummaryDTO hosted = schedulingService.schedule(host.getId(), new ScheduleMeetingRequest(
                margin.getId(), "Sync", soon(), 30, null, null,
                List.of(new ScheduleMeetingRequest.InviteeRequest(colleague.getId(), null))));

        assertTrue(schedulingService.forUser(host.getId()).stream()
                .anyMatch(m -> m.code().equals(hosted.code())), "the host sees it");
        assertTrue(schedulingService.forUser(colleague.getId()).stream()
                .anyMatch(m -> m.code().equals(hosted.code())), "an invitee sees it");
    }
}
