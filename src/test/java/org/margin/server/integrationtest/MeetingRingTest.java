package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.SubscriptionTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.meetings.models.dtos.CreateMeetingRequest;
import org.margin.server.meetings.models.dtos.GuestSessionResponse;
import org.margin.server.meetings.models.dtos.MeetingDTO;
import org.margin.server.meetings.services.MeetingAdmissionService;
import org.margin.server.meetings.services.MeetingGuestService;
import org.margin.server.meetings.services.MeetingService;
import org.margin.server.notifications.repositories.NotificationRepository;
import org.margin.server.shared.notifications.NotificationType;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.service.MarginService;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeetingRingTest extends MarginTestRunner {

    @Autowired
    private MeetingService meetingService;
    @Autowired
    private MeetingGuestService guestService;
    @Autowired
    private MeetingAdmissionService admissionService;
    @Autowired
    private MarginService marginService;
    @Autowired
    private NotificationRepository notificationRepository;

    private User host;
    private User colleague;
    private Margin margin;
    private MeetingDTO meeting;

    private void seed() {
        host = UserTestUtils.createUser("alice", "alice@margin.chat");
        colleague = UserTestUtils.createUser("bob", "bob@margin.chat");
        margin = MarginTestUtils.createMargin("Acme", host);
        SubscriptionTestUtils.setActiveSubscription(margin, "c", "s", SubscriptionTier.SMALL);
        marginService.addUserToMargin(margin.getId(), colleague.getId(), MarginRole.MEMBER, host.getId(), false);
        meeting = meetingService.createInstantMeeting(host.getId(),
                new CreateMeetingRequest(margin.getId(), "Standup", null));
    }

    @Test
    void ringingAMarginMemberLeavesThemANotification() {
        seed();

        admissionService.ring(meeting.code(), host.getId(), colleague.getId());

        assertTrue(notificationRepository.findAll().stream()
                        .anyMatch(n -> n.getType() == NotificationType.MEETING_INVITE
                                && n.getRecipientId().equals(colleague.getId())
                                && n.getReferenceId().equals(meeting.id())),
                "the invite should survive the recipient being offline");
    }

    @Test
    void someoneOutsideTheMarginCannotBeRung() {
        seed();
        User outsider = UserTestUtils.createUser("mallory", "mallory@margin.chat");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> admissionService.ring(meeting.code(), host.getId(), outsider.getId()));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void someoneOutsideTheMarginCannotRingOthers() {
        seed();
        User outsider = UserTestUtils.createUser("mallory", "mallory@margin.chat");

        assertThrows(ResponseStatusException.class,
                () -> admissionService.ring(meeting.code(), outsider.getId(), colleague.getId()));
    }

    @Test
    void aGuestCannotBeRung() {
        seed();
        GuestSessionResponse guest = guestService.createGuestSession(meeting.code(), "Wanderer");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> admissionService.ring(meeting.code(), host.getId(), guest.userId()));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void anEndedMeetingCannotBeRungInto() {
        seed();
        meetingService.end(meeting.code(), host.getId());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> admissionService.ring(meeting.code(), host.getId(), colleague.getId()));

        assertEquals(HttpStatus.GONE, e.getStatusCode());
    }
}
