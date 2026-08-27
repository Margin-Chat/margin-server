package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.SubscriptionTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.meetings.models.MeetingRole;
import org.margin.server.meetings.models.MeetingStatus;
import org.margin.server.meetings.models.dtos.CreateMeetingRequest;
import org.margin.server.meetings.models.dtos.EligibleMarginDTO;
import org.margin.server.meetings.models.dtos.MeetingDTO;
import org.margin.server.subscriptions.exceptions.SubscriptionLimitExceededException;
import org.margin.server.subscriptions.models.LimitType;
import org.margin.server.meetings.services.MeetingService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeetingTest extends MarginTestRunner {

    @Autowired
    private MeetingService meetingService;

    private User host;
    private Margin margin;

    @BeforeEach
    void setUpMeetingFixtures() {
        host = UserTestUtils.createUser("alice", "alice@margin.chat");
        margin = MarginTestUtils.createMargin("Acme", host);
        // Meetings are a paid feature; margins start on FREE.
        SubscriptionTestUtils.setActiveSubscription(margin, "cust_1", "sub_1", SubscriptionTier.SMALL);
    }

    private MeetingDTO createMeeting() {
        return meetingService.createInstantMeeting(host.getId(),
                new CreateMeetingRequest(margin.getId(), "Standup", null));
    }

    @Test
    void createInstantMeeting_startsLiveWithTheHostAsParticipant() {
        MeetingDTO meeting = createMeeting();

        assertEquals(MeetingStatus.LIVE, meeting.status());
        assertNotNull(meeting.code());
        assertEquals("Standup", meeting.title());
        assertEquals(margin.getId(), meeting.marginId());
        assertEquals(host.getId(), meeting.hostUserId());
        assertTrue(meeting.requireAdmission(), "admission should default on for public links");

        assertEquals(1, meeting.participants().size());
        assertEquals(MeetingRole.HOST, meeting.participants().getFirst().role());
    }

    @Test
    void createInstantMeeting_snapshotsTheMarginsParticipantLimit() {
        MeetingDTO meeting = createMeeting();

        assertTrue(meeting.maxParticipants() > 0,
                "the plan limit must be captured so a later tier change cannot shrink a live room");
    }

    @Test
    void createInstantMeeting_withoutAMarginIsRejected() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> meetingService.createInstantMeeting(host.getId(),
                        new CreateMeetingRequest(null, "Standup", null)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void createInstantMeeting_inAMarginYouDoNotBelongToIsForbidden() {
        User outsider = UserTestUtils.createUser("mallory", "mallory@margin.chat");

        assertThrows(ResponseStatusException.class,
                () -> meetingService.createInstantMeeting(outsider.getId(),
                        new CreateMeetingRequest(margin.getId(), "Nope", null)));
    }

    @Test
    void getByCode_isVisibleToMarginMembersAndHiddenFromEveryoneElse() {
        MeetingDTO created = createMeeting();
        User member = UserTestUtils.createUser("bob", "bob@margin.chat");
        MarginTestUtils.addUserToMargin(margin.getId(), host, member);
        User outsider = UserTestUtils.createUser("mallory", "mallory@margin.chat");

        assertEquals(created.code(), meetingService.getByCode(created.code(), member.getId()).code());
        assertThrows(ResponseStatusException.class,
                () -> meetingService.getByCode(created.code(), outsider.getId()));
    }

    @Test
    void getByCode_unknownCodeIsNotFound() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> meetingService.getByCode("does-not-exist", host.getId()));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }







    @Test
    void end_marksTheMeetingEnded() {
        MeetingDTO created = createMeeting();

        MeetingDTO ended = meetingService.end(created.code(), host.getId());

        assertEquals(MeetingStatus.ENDED, ended.status());
        assertNotNull(ended.endedAt());
    }

    @Test
    void end_byANonHostIsForbidden() {
        MeetingDTO created = createMeeting();
        User member = UserTestUtils.createUser("bob", "bob@margin.chat");
        MarginTestUtils.addUserToMargin(margin.getId(), host, member);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> meetingService.end(created.code(), member.getId()));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void meetingCodes_areUniquePerMeeting() {
        assertNotEquals(createMeeting().code(), createMeeting().code());
    }

    @Test
    void createInstantMeeting_onAFreeMarginIsRejectedAsPaymentRequired() {
        User freeHost = UserTestUtils.createUser("frank", "frank@margin.chat");
        Margin freeMargin = MarginTestUtils.createMargin("Frugal", freeHost);

        SubscriptionLimitExceededException e = assertThrows(SubscriptionLimitExceededException.class,
                () -> meetingService.createInstantMeeting(freeHost.getId(),
                        new CreateMeetingRequest(freeMargin.getId(), "Standup", null)));

        assertEquals(HttpStatus.PAYMENT_REQUIRED, e.getStatus());
        assertEquals(LimitType.MEETINGS, e.getLimit());
        assertEquals(SubscriptionTier.FREE, e.getTier());
    }

    @Test
    void eligibleMargins_listsOnlyPaidMarginsTheUserBelongsTo() {
        Margin freeMargin = MarginTestUtils.createMargin("Frugal", host);
        User outsiderHost = UserTestUtils.createUser("olivia", "olivia@margin.chat");
        Margin someoneElsesPaidMargin = MarginTestUtils.createMargin("NotMine", outsiderHost);
        SubscriptionTestUtils.setActiveSubscription(someoneElsesPaidMargin, "c2", "s2", SubscriptionTier.SMALL);

        List<EligibleMarginDTO> eligible = meetingService.eligibleMargins(host.getId());

        assertEquals(List.of(margin.getId()), eligible.stream().map(EligibleMarginDTO::marginId).toList());
        assertTrue(eligible.stream().noneMatch(m -> m.marginId().equals(freeMargin.getId())),
                "free margins cannot host meetings");
        assertTrue(eligible.stream().noneMatch(m -> m.marginId().equals(someoneElsesPaidMargin.getId())),
                "membership is still required");
    }

    @Test
    void eligibleMargins_isEmptyWhenEveryMarginIsFree() {
        User freeHost = UserTestUtils.createUser("frank", "frank@margin.chat");
        MarginTestUtils.createMargin("Frugal", freeHost);

        assertTrue(meetingService.eligibleMargins(freeHost.getId()).isEmpty());
    }

    @Test
    void downgradingDoesNotEndOrLockAnExistingMeeting() {
        MeetingDTO created = createMeeting();
        SubscriptionTestUtils.setActiveSubscription(margin, "cust_1", "sub_1", SubscriptionTier.FREE);

        assertDoesNotThrow(() -> meetingService.getByCode(created.code(), host.getId()),
                "a downgrade must not lock people out of a meeting that already exists");
    }
}
