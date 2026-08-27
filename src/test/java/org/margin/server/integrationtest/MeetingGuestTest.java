package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.SubscriptionTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.meetings.models.ParticipantState;
import org.margin.server.meetings.models.dtos.ClaimGuestRequest;
import org.margin.server.meetings.models.dtos.CreateMeetingRequest;
import org.margin.server.meetings.models.dtos.GuestSessionResponse;
import org.margin.server.meetings.models.dtos.MeetingDTO;
import org.margin.server.meetings.models.dtos.MeetingPreviewDTO;
import org.margin.server.meetings.repositories.MeetingParticipantRepository;
import org.margin.server.meetings.security.MeetingGuestPrincipal;
import org.margin.server.meetings.services.MeetingGuestService;
import org.margin.server.meetings.services.MeetingGuestTokenService;
import org.margin.server.meetings.services.MeetingService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.users.api.UserLookup;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeetingGuestTest extends MarginTestRunner {

    @Autowired
    private MeetingService meetingService;
    @Autowired
    private MeetingGuestService guestService;
    @Autowired
    private MeetingGuestTokenService guestTokenService;
    @Autowired
    private MeetingParticipantRepository participantRepository;
    @Autowired
    private UserLookup userLookup;
    @Autowired
    private org.margin.server.authentication.services.JwtService jwtService;

    private MeetingDTO meeting;

    private MeetingDTO newMeeting() {
        User host = UserTestUtils.createUser("alice", "alice@margin.chat");
        Margin margin = MarginTestUtils.createMargin("Acme", host);
        SubscriptionTestUtils.setActiveSubscription(margin, "c", "s", SubscriptionTier.SMALL);
        return meetingService.createInstantMeeting(host.getId(),
                new CreateMeetingRequest(margin.getId(), "Standup", null));
    }

    @Test
    void previewExposesEnoughToRenderALobbyAndNothingMore() {
        meeting = newMeeting();

        MeetingPreviewDTO preview = guestService.preview(meeting.code());

        assertEquals("Standup", preview.title());
        assertEquals("Acme", preview.marginName());
        assertEquals("alice", preview.hostDisplayName());
        assertTrue(preview.joinable());
    }

    @Test
    void previewOfAnUnknownMeetingIsNotFound() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> guestService.preview("does-not-exist"));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    void aGuestSessionCreatesAKnockingParticipantAndAGuestAccount() {
        meeting = newMeeting();

        GuestSessionResponse session = guestService.createGuestSession(meeting.code(), "Wanderer");

        assertNotNull(session.guestToken());
        assertEquals("Wanderer", session.displayName());
        assertTrue(session.requiresAdmission());
        assertTrue(userLookup.isGuest(session.userId()));

        var participant = participantRepository
                .findByMeetingIdAndUserId(meeting.id(), session.userId()).orElseThrow();
        assertEquals(ParticipantState.KNOCKING, participant.getState());
        assertTrue(participant.isGuest());
    }

    @Test
    void aBlankDisplayNameFallsBackRatherThanFailing() {
        meeting = newMeeting();

        assertEquals("Guest", guestService.createGuestSession(meeting.code(), "   ").displayName());
    }

    @Test
    void anOverlongDisplayNameIsTruncatedToTheColumnWidth() {
        meeting = newMeeting();

        String name = guestService.createGuestSession(meeting.code(), "x".repeat(200)).displayName();

        assertEquals(50, name.length());
    }

    @Test
    void joiningAnEndedMeetingIsGone() {
        meeting = newMeeting();
        User host = UserTestUtils.findById(meeting.hostUserId());
        meetingService.end(meeting.code(), host.getId());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> guestService.createGuestSession(meeting.code(), "Late"));

        assertEquals(HttpStatus.GONE, e.getStatusCode());
    }

    @Test
    void aGuestTokenRoundTripsToItsPrincipal() {
        meeting = newMeeting();
        GuestSessionResponse session = guestService.createGuestSession(meeting.code(), "Wanderer");

        Optional<MeetingGuestPrincipal> principal = guestTokenService.parse(session.guestToken());

        assertTrue(principal.isPresent());
        assertEquals(session.userId(), principal.get().userId());
        assertEquals(meeting.code(), principal.get().meetingCode());
        assertEquals("Wanderer", principal.get().displayName());
    }

    @Test
    void aMarginJwtIsNotAcceptedAsAGuestToken() {
        meeting = newMeeting();
        GuestSessionResponse valid = guestService.createGuestSession(meeting.code(), "Wanderer");
        User member = UserTestUtils.createUser("bob", "bob@margin.chat");
        String marginJwt = jwtService.generateToken(member.getEmail(), member.getId(), 0);

        assertTrue(guestTokenService.parse(valid.guestToken()).isPresent(),
                "positive control: a real guest token must parse");
        assertTrue(guestTokenService.parse(marginJwt).isEmpty(),
                "the two token systems must not be interchangeable");
    }

    @Test
    void claimingWithAFreeEmailPromotesTheSameUserId() {
        meeting = newMeeting();
        GuestSessionResponse session = guestService.createGuestSession(meeting.code(), "Wanderer");
        MeetingGuestPrincipal principal = guestTokenService.parse(session.guestToken()).orElseThrow();

        guestService.claim(principal, new ClaimGuestRequest(
                "wanderer@example.com", "hunter2", null, null, null, null));

        assertFalse(userLookup.isGuest(session.userId()));
        assertEquals(session.userId(),
                participantRepository.findByMeetingIdAndUserId(meeting.id(), session.userId())
                        .orElseThrow().getUserId());
    }

    @Test
    void claimingWithAnEmailThatAlreadyExistsIsAConflict() {
        meeting = newMeeting();
        UserTestUtils.createUser("taken", "taken@margin.chat");
        GuestSessionResponse session = guestService.createGuestSession(meeting.code(), "Wanderer");
        MeetingGuestPrincipal principal = guestTokenService.parse(session.guestToken()).orElseThrow();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> guestService.claim(principal, new ClaimGuestRequest(
                        "taken@margin.chat", "hunter2", null, null, null, null)));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertTrue(userLookup.isGuest(session.userId()), "the guest keeps their seat on a failed claim");
    }

    @Test
    void claimingTwiceIsAConflict() {
        meeting = newMeeting();
        GuestSessionResponse session = guestService.createGuestSession(meeting.code(), "Wanderer");
        MeetingGuestPrincipal principal = guestTokenService.parse(session.guestToken()).orElseThrow();
        guestService.claim(principal, new ClaimGuestRequest(
                "wanderer@example.com", "hunter2", null, null, null, null));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> guestService.claim(principal, new ClaimGuestRequest(
                        "other@example.com", "hunter2", null, null, null, null)));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }
}
