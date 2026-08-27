package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.SubscriptionTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.meetings.models.ParticipantState;
import org.margin.server.meetings.models.dtos.CreateMeetingRequest;
import org.margin.server.meetings.models.dtos.GuestSessionResponse;
import org.margin.server.meetings.models.dtos.MeetingDTO;
import org.margin.server.meetings.models.dtos.MeetingParticipantDTO;
import org.margin.server.meetings.repositories.MeetingParticipantRepository;
import org.margin.server.meetings.security.MeetingGuestPrincipal;
import org.margin.server.meetings.services.MeetingAdmissionService;
import org.margin.server.meetings.services.MeetingGuestService;
import org.margin.server.meetings.services.MeetingGuestTokenService;
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
class MeetingAdmissionTest extends MarginTestRunner {

    @Autowired
    private MeetingService meetingService;
    @Autowired
    private MeetingGuestService guestService;
    @Autowired
    private MeetingGuestTokenService guestTokenService;
    @Autowired
    private MeetingAdmissionService admissionService;
    @Autowired
    private MeetingParticipantRepository participantRepository;

    private User host;
    private MeetingDTO meeting;

    private void seed() {
        seed("alice@margin.chat");
    }

    private void seed(String hostEmail) {
        host = UserTestUtils.createUser("alice", hostEmail);
        Margin margin = MarginTestUtils.createMargin("Acme", host);
        SubscriptionTestUtils.setActiveSubscription(margin, "c", "s", SubscriptionTier.SMALL);
        meeting = meetingService.createInstantMeeting(host.getId(),
                new CreateMeetingRequest(margin.getId(), "Standup", null));
    }

    private GuestSessionResponse guest(String name) {
        return guestService.createGuestSession(meeting.code(), name);
    }

    private Long participantIdOf(GuestSessionResponse session) {
        return participantRepository.findByMeetingIdAndUserId(meeting.id(), session.userId())
                .orElseThrow().getId();
    }

    @Test
    void aKnockingGuestShowsUpInTheHostsLobbyList() {
        seed();
        GuestSessionResponse session = guest("Wanderer");

        List<MeetingParticipantDTO> waiting = admissionService.waiting(meeting.code(), host.getId());

        assertEquals(1, waiting.size());
        assertEquals("Wanderer", waiting.getFirst().displayName());
        assertTrue(waiting.getFirst().guest());
        assertEquals(session.userId(), waiting.getFirst().userId());
    }

    @Test
    void admittingMovesTheGuestOutOfTheLobby() {
        seed();
        GuestSessionResponse session = guest("Wanderer");

        admissionService.admit(meeting.code(), host.getId(), participantIdOf(session));

        assertEquals(ParticipantState.ADMITTED,
                participantRepository.findById(participantIdOf(session)).orElseThrow().getState());
        assertTrue(admissionService.waiting(meeting.code(), host.getId()).isEmpty());
    }

    @Test
    void admittingTwiceIsANoOpRatherThanAnError() {
        seed();
        GuestSessionResponse session = guest("Wanderer");
        Long participantId = participantIdOf(session);
        admissionService.admit(meeting.code(), host.getId(), participantId);

        assertDoesNotThrow(() -> admissionService.admit(meeting.code(), host.getId(), participantId),
                "two co-hosts clicking admit must not produce an error for the second");
    }

    @Test
    void onlyAHostCanAdmit() {
        seed();
        GuestSessionResponse session = guest("Wanderer");
        User outsider = UserTestUtils.createUser("mallory", "mallory@margin.chat");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> admissionService.admit(meeting.code(), outsider.getId(), participantIdOf(session)));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void aGuestCannotAdmitThemselves() {
        seed();
        GuestSessionResponse session = guest("Wanderer");

        assertThrows(ResponseStatusException.class,
                () -> admissionService.admit(meeting.code(), session.userId(), participantIdOf(session)));
    }

    @Test
    void denyingIsStickyAcrossAReKnock() {
        seed();
        GuestSessionResponse session = guest("Wanderer");
        admissionService.deny(meeting.code(), host.getId(), participantIdOf(session));

        admissionService.knock(meeting.id(), session.userId());

        assertEquals(ParticipantState.DENIED,
                participantRepository.findById(participantIdOf(session)).orElseThrow().getState(),
                "a denied guest must not be able to re-enter the queue by knocking again");
    }

    @Test
    void anAdmittedGuestReturningIsNotSentBackToTheQueue() {
        seed();
        GuestSessionResponse session = guest("Wanderer");
        admissionService.admit(meeting.code(), host.getId(), participantIdOf(session));

        admissionService.knock(meeting.id(), session.userId());

        assertEquals(ParticipantState.ADMITTED,
                participantRepository.findById(participantIdOf(session)).orElseThrow().getState(),
                "a refresh must not require a second admit");
        assertTrue(admissionService.waiting(meeting.code(), host.getId()).isEmpty());
    }

    @Test
    void resumeReportsAdmissionStateAfterARefresh() {
        seed();
        GuestSessionResponse session = guest("Wanderer");
        MeetingGuestPrincipal principal = guestTokenService.parse(session.guestToken()).orElseThrow();

        assertTrue(guestService.resume(principal).requiresAdmission(), "still waiting before admit");

        admissionService.admit(meeting.code(), host.getId(), participantIdOf(session));

        assertFalse(guestService.resume(principal).requiresAdmission(), "admitted after admit");
    }

    @Test
    void aRemovedGuestCannotResume() {
        seed();
        GuestSessionResponse session = guest("Wanderer");
        MeetingGuestPrincipal principal = guestTokenService.parse(session.guestToken()).orElseThrow();
        admissionService.remove(meeting.code(), host.getId(), participantIdOf(session));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> guestService.resume(principal));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void theHostCannotBeRemoved() {
        seed();
        Long hostParticipantId = participantRepository
                .findByMeetingIdAndUserId(meeting.id(), host.getId()).orElseThrow().getId();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> admissionService.remove(meeting.code(), host.getId(), hostParticipantId));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test
    void aParticipantFromAnotherMeetingCannotBeAdmittedHere() {
        seed();
        GuestSessionResponse session = guest("Wanderer");
        Long foreignParticipantId = participantIdOf(session);

        MeetingDTO first = meeting;
        seed("alice-second@margin.chat");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> admissionService.admit(meeting.code(), host.getId(), foreignParticipantId));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertNotEquals(first.code(), meeting.code());
    }
}
