package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.meetings.entities.Meeting;
import org.margin.server.meetings.entities.MeetingParticipant;
import org.margin.server.meetings.models.MeetingRole;
import org.margin.server.meetings.models.MeetingStatus;
import org.margin.server.meetings.models.ParticipantState;
import org.margin.server.meetings.models.dtos.MeetingJoinResponse;
import org.margin.server.meetings.repositories.MeetingParticipantRepository;
import org.margin.server.meetings.repositories.MeetingRepository;
import org.margin.server.meetings.services.MeetingService;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.sfu.services.SfuService;
import org.margin.server.sfu.services.SfuTokenService;
import org.margin.server.social.api.MarginLookup;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
import org.margin.server.users.api.UserLookup;
import org.margin.server.users.api.UserSummary;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The join/end paths, which are the ones that talk to the SFU. Persistence and authorization
 * live in MeetingTest.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeetingServiceTest {

    private static final String CODE = "abc123";
    private static final Long HOST_ID = 7L;
    private static final Long MARGIN_ID = 3L;

    @Mock
    private MeetingRepository meetingRepository;
    @Mock
    private MeetingParticipantRepository participantRepository;
    @Mock
    private MarginAccessChecker marginAccessChecker;
    @Mock
    private SubscriptionValidationService subscriptionValidationService;
    @Mock
    private SfuService sfuService;
    @Mock
    private SfuTokenService sfuTokenService;
    @Mock
    private MarginLookup marginLookup;
    @Mock
    private UserLookup userLookup;

    @InjectMocks
    private MeetingService meetingService;

    private Meeting meeting;

    @BeforeEach
    void setUp() {
        meeting = new Meeting();
        meeting.setId(1L);
        meeting.setCode(CODE);
        meeting.setHostUserId(HOST_ID);
        meeting.setMarginId(MARGIN_ID);
        meeting.setStatus(MeetingStatus.LIVE);
        meeting.setMaxParticipants(10);
        meeting.setMaxVideoHeight(720);

        when(meetingRepository.findByCode(CODE)).thenReturn(Optional.of(meeting));
        when(meetingRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(participantRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(participantRepository.findByMeetingId(anyLong())).thenReturn(List.of());
        when(sfuService.peerIdsInRoom(any())).thenReturn(List.of());
        when(sfuService.getSfuPublicUrl()).thenReturn("ws://sfu.test");
        when(sfuTokenService.generateRoomToken(anyLong(), anyString(), anyString())).thenReturn("room-token");
        when(userLookup.summaryOf(anyLong())).thenReturn(new UserSummary(HOST_ID, "Alice"));
        when(marginLookup.summaryOf(anyLong())).thenReturn(new MarginLookup.MarginSummary(MARGIN_ID, "Acme", 1));
    }

    @Test
    @DisplayName("join addresses a prefixed meeting room, never a channel-shaped id")
    void join_usesAPrefixedRoomId() {
        MeetingJoinResponse response = meetingService.join(CODE, HOST_ID);

        assertThat(response.roomId()).isEqualTo("m_" + CODE);
        assertThat(response.roomToken()).isEqualTo("room-token");
        assertThat(response.sfuUrl()).isEqualTo("ws://sfu.test");
        assertThat(response.maxVideoHeight()).isEqualTo(720);
        verify(sfuService).createOrJoinRoom("m_" + CODE, 10);
    }

    @Test
    @DisplayName("join hands back the peer identity the room token was minted for")
    void join_returnsThePeerIdentity() {
        meeting.setTitle("Standup");

        MeetingJoinResponse response = meetingService.join(CODE, HOST_ID);

        assertThat(response.peerId()).isEqualTo(String.valueOf(HOST_ID));
        assertThat(response.displayName()).isEqualTo("Alice");
        assertThat(response.title()).isEqualTo("Standup");
    }

    @Test
    @DisplayName("an admitted guest joins on their shadow user, with no margin membership check")
    void joinAsGuest_returnsTheShadowPeerIdentity() {
        MeetingParticipant guest = new MeetingParticipant();
        guest.setMeetingId(1L);
        guest.setUserId(42L);
        guest.setDisplayName("Wanderer");
        guest.setGuest(true);
        guest.setRole(MeetingRole.PARTICIPANT);
        guest.setState(ParticipantState.ADMITTED);
        when(participantRepository.findByMeetingIdAndUserId(1L, 42L)).thenReturn(Optional.of(guest));

        MeetingJoinResponse response = meetingService.joinAsGuest(CODE, 42L);

        assertThat(response.peerId()).isEqualTo("42");
        assertThat(response.displayName()).isEqualTo("Wanderer");
        verify(sfuTokenService).generateRoomToken(42L, "m_" + CODE, "Wanderer");
        verify(marginAccessChecker, never()).requireMarginMember(anyLong(), anyLong());
    }

    @Test
    @DisplayName("join mints the room token against the joining user, not the host")
    void join_mintsTokenForTheJoiningUser() {
        when(userLookup.summaryOf(9L)).thenReturn(new UserSummary(9L, "Bob"));

        meetingService.join(CODE, 9L);

        verify(sfuTokenService).generateRoomToken(9L, "m_" + CODE, "Bob");
    }

    @Test
    @DisplayName("join records the participant as JOINED")
    void join_marksParticipantJoined() {
        meetingService.join(CODE, HOST_ID);

        ArgumentCaptor<MeetingParticipant> captor = ArgumentCaptor.forClass(MeetingParticipant.class);
        verify(participantRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getState()).isEqualTo(ParticipantState.JOINED);
        assertThat(captor.getValue().getJoinedAt()).isNotNull();
    }

    @Test
    @DisplayName("a full room is rejected before the SFU is asked to create it")
    void join_rejectsAFullRoom() {
        when(sfuService.peerIdsInRoom(any()))
                .thenReturn(IntStream.range(0, 10).mapToObj(String::valueOf).toList());

        assertThatThrownBy(() -> meetingService.join(CODE, HOST_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(sfuService, never()).createOrJoinRoom(anyString(), anyInt());
    }

    @Test
    @DisplayName("an ended meeting cannot be rejoined")
    void join_rejectsAnEndedMeeting() {
        meeting.setStatus(MeetingStatus.ENDED);

        assertThatThrownBy(() -> meetingService.join(CODE, HOST_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.GONE);
    }

    @Test
    @DisplayName("a scheduled meeting goes live on the first arrival, not on a clock")
    void join_flipsScheduledToLive() {
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setScheduledAt(Instant.now());
        meeting.setStartedAt(null);

        meetingService.join(CODE, HOST_ID);

        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.LIVE);
        assertThat(meeting.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("joining twice reuses the existing participant row")
    void join_reusesAnExistingParticipant() {
        MeetingParticipant existing = new MeetingParticipant();
        existing.setMeetingId(1L);
        existing.setUserId(HOST_ID);
        existing.setRole(MeetingRole.HOST);
        existing.setState(ParticipantState.ADMITTED);
        when(participantRepository.findByMeetingIdAndUserId(1L, HOST_ID)).thenReturn(Optional.of(existing));

        meetingService.join(CODE, HOST_ID);

        assertThat(existing.getState()).isEqualTo(ParticipantState.JOINED);
        assertThat(existing.getRole()).isEqualTo(MeetingRole.HOST);
    }

    @Test
    @DisplayName("ending frees the SFU room rather than waiting out the idle reaper")
    void end_closesTheRoom() {
        meetingService.end(CODE, HOST_ID);

        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.ENDED);
        assertThat(meeting.getEndedAt()).isNotNull();
        verify(sfuService).closeRoom("m_" + CODE);
    }

    @Test
    @DisplayName("only the host may end a meeting")
    void end_rejectsNonHosts() {
        assertThatThrownBy(() -> meetingService.end(CODE, 9L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(sfuService, never()).closeRoom(anyString());
    }

    @Test
    @DisplayName("access is gated on membership of the meeting's margin")
    void join_checksMarginMembership() {
        meetingService.join(CODE, HOST_ID);

        verify(marginAccessChecker).requireMarginMember(HOST_ID, MARGIN_ID);
    }
}
