package org.margin.server.meetings.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.meetings.entities.Meeting;
import org.margin.server.meetings.entities.MeetingParticipant;
import org.margin.server.meetings.models.MeetingRole;
import org.margin.server.meetings.models.MeetingStatus;
import org.margin.server.meetings.models.ParticipantState;
import org.margin.server.meetings.models.dtos.CreateMeetingRequest;
import org.margin.server.meetings.models.dtos.EligibleMarginDTO;
import org.margin.server.meetings.models.dtos.MeetingDTO;
import org.margin.server.meetings.models.dtos.MeetingJoinResponse;
import org.margin.server.meetings.models.dtos.MeetingParticipantDTO;
import org.margin.server.meetings.repositories.MeetingParticipantRepository;
import org.margin.server.meetings.repositories.MeetingRepository;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.shared.voice.RoomKey;
import org.margin.server.sfu.services.SfuService;
import org.margin.server.sfu.services.SfuTokenService;
import org.margin.server.social.api.MarginLookup;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
import org.margin.server.users.api.UserLookup;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@NamedInterface("api")
@Slf4j
@Service
public class MeetingService {
    private static final Duration INSTANT_MEETING_LIFETIME = Duration.ofHours(12);
    private static final int FREE_TIER_MAX_VIDEO_HEIGHT = 720;

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MarginAccessChecker marginAccessChecker;
    private final SubscriptionValidationService subscriptionValidationService;
    private final SfuService sfuService;
    private final SfuTokenService sfuTokenService;
    private final MarginLookup marginLookup;
    private final UserLookup userLookup;

    public MeetingService(MeetingRepository meetingRepository,
                          MeetingParticipantRepository participantRepository,
                          MarginAccessChecker marginAccessChecker,
                          SubscriptionValidationService subscriptionValidationService,
                          SfuService sfuService,
                          SfuTokenService sfuTokenService,
                          MarginLookup marginLookup,
                          UserLookup userLookup) {
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
        this.marginAccessChecker = marginAccessChecker;
        this.subscriptionValidationService = subscriptionValidationService;
        this.sfuService = sfuService;
        this.sfuTokenService = sfuTokenService;
        this.marginLookup = marginLookup;
        this.userLookup = userLookup;
    }

    @Transactional
    public MeetingDTO createInstantMeeting(Long hostUserId, CreateMeetingRequest request) {
        if (request.marginId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "marginId is required");
        }
        marginAccessChecker.requireMarginMember(hostUserId, request.marginId());
        subscriptionValidationService.validateMeetingCreation(request.marginId());

        Meeting meeting = new Meeting();
        meeting.setCode(newCode());
        meeting.setHostUserId(hostUserId);
        meeting.setMarginId(request.marginId());
        meeting.setTitle(request.title());
        meeting.setStatus(MeetingStatus.LIVE);
        meeting.setStartedAt(Instant.now());
        meeting.setRequireAdmission(request.requireAdmission() == null || request.requireAdmission());
        meeting.setExpiresAt(Instant.now().plus(INSTANT_MEETING_LIFETIME));

        meeting.setMaxParticipants(subscriptionValidationService.getMaxCallParticipants(request.marginId()));
        meeting.setMaxVideoHeight(
                subscriptionValidationService.tierForMargin(request.marginId()) == SubscriptionTier.FREE
                        ? FREE_TIER_MAX_VIDEO_HEIGHT : null);

        Meeting saved = meetingRepository.save(meeting);
        addParticipant(saved.getId(), hostUserId, MeetingRole.HOST);

        log.info("Meeting {} created by userId {} in margin {}", saved.getCode(), hostUserId, saved.getMarginId());
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<EligibleMarginDTO> eligibleMargins(Long userId) {
        return marginLookup.marginsForUser(userId).stream()
                .filter(m -> subscriptionValidationService.canCreateMeetings(m.id()))
                .map(m -> new EligibleMarginDTO(m.id(), m.name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MeetingDTO getByCode(String code, Long userId) {
        Meeting meeting = requireMeeting(code);
        requireCanAccess(meeting, userId);
        return toDTO(meeting);
    }

    @Transactional
    public MeetingJoinResponse join(String code, Long userId) {
        Meeting meeting = requireMeeting(code);
        requireCanAccess(meeting, userId);

        if (!meeting.isJoinable()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Meeting has ended");
        }

        RoomKey room = new RoomKey.MeetingRoom(meeting.getCode());
        int connected = sfuService.peerIdsInRoom(room).size();
        if (connected >= meeting.getMaxParticipants()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MEETING_FULL");
        }

        sfuService.createOrJoinRoom(room.value(), meeting.getMaxParticipants());

        MeetingParticipant participant = participantRepository
                .findByMeetingIdAndUserId(meeting.getId(), userId)
                .orElseGet(() -> addParticipant(meeting.getId(), userId, MeetingRole.PARTICIPANT));
        participant.setState(ParticipantState.JOINED);
        participant.setJoinedAt(Instant.now());
        participantRepository.save(participant);

        if (meeting.getStatus() == MeetingStatus.SCHEDULED) {
            meeting.setStatus(MeetingStatus.LIVE);
            meeting.setStartedAt(Instant.now());
            meetingRepository.save(meeting);
        }

        String token = sfuTokenService.generateRoomToken(
                userId, room.value(), userLookup.summaryOf(userId).displayName());

        return new MeetingJoinResponse(
                sfuService.getSfuPublicUrl(), room.value(), token, meeting.getMaxVideoHeight());
    }

    @Transactional
    public MeetingDTO end(String code, Long userId) {
        Meeting meeting = requireMeeting(code);
        requireHost(meeting, userId);

        meeting.setStatus(MeetingStatus.ENDED);
        meeting.setEndedAt(Instant.now());
        meetingRepository.save(meeting);

        sfuService.closeRoom(new RoomKey.MeetingRoom(meeting.getCode()).value());

        log.info("Meeting {} ended by userId {}", code, userId);
        return toDTO(meeting);
    }

    private MeetingParticipant addParticipant(Long meetingId, Long userId, MeetingRole role) {
        MeetingParticipant participant = new MeetingParticipant();
        participant.setMeetingId(meetingId);
        participant.setUserId(userId);
        participant.setDisplayName(userLookup.summaryOf(userId).displayName());
        participant.setGuest(userLookup.isGuest(userId));
        participant.setRole(role);
        participant.setState(ParticipantState.ADMITTED);
        participant.setAdmittedAt(Instant.now());
        return participantRepository.save(participant);
    }

    private Meeting requireMeeting(String code) {
        return meetingRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meeting not found"));
    }

    private void requireCanAccess(Meeting meeting, Long userId) {
        marginAccessChecker.requireMarginMember(userId, meeting.getMarginId());
    }

    private void requireHost(Meeting meeting, Long userId) {
        if (!meeting.getHostUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can do that");
        }
    }

    private MeetingDTO toDTO(Meeting meeting) {
        Set<String> connected = Set.copyOf(
                sfuService.peerIdsInRoom(new RoomKey.MeetingRoom(meeting.getCode())));

        List<MeetingParticipantDTO> participants = participantRepository.findByMeetingId(meeting.getId())
                .stream()
                .map(p -> new MeetingParticipantDTO(
                        p.getId(),
                        p.getUserId(),
                        p.getDisplayName(),
                        p.isGuest(),
                        p.getRole(),
                        p.getState(),
                        p.getUserId() != null && connected.contains(String.valueOf(p.getUserId()))))
                .toList();

        return new MeetingDTO(
                meeting.getId(),
                meeting.getCode(),
                meeting.getTitle(),
                meeting.getStatus(),
                meeting.getMarginId(),
                marginLookup.summaryOf(meeting.getMarginId()).name(),
                meeting.getHostUserId(),
                meeting.isRequireAdmission(),
                meeting.getMaxParticipants(),
                meeting.getScheduledAt(),
                meeting.getDurationMinutes(),
                meeting.getStartedAt(),
                meeting.getEndedAt(),
                participants);
    }

    private String newCode() {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (uuid.getMostSignificantBits() >>> (8 * (7 - i)));
            bytes[8 + i] = (byte) (uuid.getLeastSignificantBits() >>> (8 * (7 - i)));
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
