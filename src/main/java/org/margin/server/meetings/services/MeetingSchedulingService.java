package org.margin.server.meetings.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.meetings.entities.Meeting;
import org.margin.server.meetings.entities.MeetingInvite;
import org.margin.server.meetings.entities.MeetingParticipant;
import org.margin.server.meetings.models.MeetingInviteStatus;
import org.margin.server.meetings.models.MeetingRole;
import org.margin.server.meetings.models.MeetingStatus;
import org.margin.server.meetings.models.ParticipantState;
import org.margin.server.meetings.models.dtos.MeetingInviteDTO;
import org.margin.server.meetings.models.dtos.MeetingSummaryDTO;
import org.margin.server.meetings.models.dtos.RescheduleMeetingRequest;
import org.margin.server.meetings.models.dtos.ScheduleMeetingRequest;
import org.margin.server.meetings.repositories.MeetingInviteRepository;
import org.margin.server.meetings.repositories.MeetingParticipantRepository;
import org.margin.server.meetings.repositories.MeetingRepository;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.social.api.MarginLookup;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
import org.margin.server.users.api.UserLookup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class MeetingSchedulingService {

    private static final Duration SCHEDULED_MEETING_GRACE = Duration.ofHours(24);
    private static final int FREE_TIER_MAX_VIDEO_HEIGHT = 720;
    private static final int MAX_INVITEES = 100;

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingInviteRepository inviteRepository;
    private final MarginAccessChecker marginAccessChecker;
    private final SubscriptionValidationService subscriptions;
    private final MarginLookup marginLookup;
    private final UserLookup userLookup;

    public MeetingSchedulingService(MeetingRepository meetingRepository,
                                    MeetingParticipantRepository participantRepository,
                                    MeetingInviteRepository inviteRepository,
                                    MarginAccessChecker marginAccessChecker,
                                    SubscriptionValidationService subscriptions,
                                    MarginLookup marginLookup,
                                    UserLookup userLookup) {
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
        this.inviteRepository = inviteRepository;
        this.marginAccessChecker = marginAccessChecker;
        this.subscriptions = subscriptions;
        this.marginLookup = marginLookup;
        this.userLookup = userLookup;
    }

    @Transactional
    public MeetingSummaryDTO schedule(Long hostUserId, ScheduleMeetingRequest request) {
        if (request.marginId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "marginId is required");
        }
        if (request.scheduledAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledAt is required");
        }
        if (request.scheduledAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledAt is in the past");
        }
        if (request.durationMinutes() != null && request.durationMinutes() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationMinutes must be positive");
        }
        if (request.invitees() != null && request.invitees().size() > MAX_INVITEES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many invitees");
        }

        marginAccessChecker.requireMarginMember(hostUserId, request.marginId());
        subscriptions.validateMeetingCreation(request.marginId());

        Meeting meeting = new Meeting();
        meeting.setCode(MeetingCodes.newCode());
        meeting.setHostUserId(hostUserId);
        meeting.setMarginId(request.marginId());
        meeting.setTitle(request.title());
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setScheduledAt(request.scheduledAt());
        meeting.setDurationMinutes(request.durationMinutes());
        meeting.setOrganizerTimezone(validTimezone(request.organizerTimezone()));
        meeting.setRequireAdmission(request.requireAdmission() == null || request.requireAdmission());
        meeting.setMaxParticipants(subscriptions.getMaxCallParticipants(request.marginId()));
        meeting.setMaxVideoHeight(
                subscriptions.tierForMargin(request.marginId()) == SubscriptionTier.FREE
                        ? FREE_TIER_MAX_VIDEO_HEIGHT : null);
        meeting.setExpiresAt(expiryFor(request.scheduledAt(), request.durationMinutes()));

        Meeting saved = meetingRepository.save(meeting);
        addHost(saved.getId(), hostUserId);
        addInvitees(saved, request.invitees());

        log.info("Meeting {} scheduled for {} by userId {}", saved.getCode(), saved.getScheduledAt(), hostUserId);
        return toSummary(saved);
    }

    @Transactional
    public MeetingSummaryDTO reschedule(String code, Long actingUserId, RescheduleMeetingRequest request) {
        Meeting meeting = requireMeeting(code);
        requireHost(meeting, actingUserId);

        if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a scheduled meeting can be moved");
        }
        if (request.scheduledAt() == null || request.scheduledAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledAt must be in the future");
        }

        meeting.setScheduledAt(request.scheduledAt());
        if (request.durationMinutes() != null) {
            meeting.setDurationMinutes(request.durationMinutes());
        }
        if (request.title() != null) {
            meeting.setTitle(request.title());
        }
        meeting.setExpiresAt(expiryFor(request.scheduledAt(), meeting.getDurationMinutes()));
        meeting.setIcsSequence(meeting.getIcsSequence() + 1);
        meeting.setReminderSentAt(null);

        return toSummary(meetingRepository.save(meeting));
    }

    @Transactional
    public MeetingSummaryDTO cancel(String code, Long actingUserId) {
        Meeting meeting = requireMeeting(code);
        requireHost(meeting, actingUserId);

        if (meeting.getStatus() != MeetingStatus.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a scheduled meeting can be cancelled; end it instead");
        }

        meeting.setStatus(MeetingStatus.CANCELLED);
        meeting.setCancelledAt(Instant.now());
        meeting.setIcsSequence(meeting.getIcsSequence() + 1);

        return toSummary(meetingRepository.save(meeting));
    }

    @Transactional(readOnly = true)
    public List<MeetingSummaryDTO> forUser(Long userId) {
        return meetingRepository.findForUser(userId).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<MeetingInviteDTO> invitees(String code, Long actingUserId) {
        Meeting meeting = requireMeeting(code);
        marginAccessChecker.requireMarginMember(actingUserId, meeting.getMarginId());

        return inviteRepository.findByMeetingId(meeting.getId()).stream()
                .map(i -> new MeetingInviteDTO(
                        i.getId(),
                        i.getUserId(),
                        i.getUserId() == null ? null : userLookup.summaryOf(i.getUserId()).displayName(),
                        i.getEmail(),
                        i.getStatus()))
                .toList();
    }

    private void addInvitees(Meeting meeting, List<ScheduleMeetingRequest.InviteeRequest> invitees) {
        if (invitees == null) {
            return;
        }

        List<MeetingInvite> rows = new ArrayList<>();
        for (ScheduleMeetingRequest.InviteeRequest invitee : invitees) {
            if (invitee.userId() == null && (invitee.email() == null || invitee.email().isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An invitee needs a user or an email");
            }
            if (invitee.userId() != null && userLookup.isGuest(invitee.userId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot invite a guest account");
            }
            if (invitee.email() != null
                    && invitee.email().toLowerCase().endsWith("@guests.margin.invalid")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot invite a guest address");
            }

            MeetingInvite invite = new MeetingInvite();
            invite.setMeetingId(meeting.getId());
            invite.setUserId(invitee.userId());
            invite.setEmail(invitee.email() == null ? null : invitee.email().toLowerCase());
            invite.setInviteToken(UUID.randomUUID().toString());
            invite.setStatus(MeetingInviteStatus.PENDING);
            rows.add(invite);
        }
        inviteRepository.saveAll(rows);
    }

    private void addHost(Long meetingId, Long hostUserId) {
        MeetingParticipant participant = new MeetingParticipant();
        participant.setMeetingId(meetingId);
        participant.setUserId(hostUserId);
        participant.setDisplayName(userLookup.summaryOf(hostUserId).displayName());
        participant.setGuest(false);
        participant.setRole(MeetingRole.HOST);
        participant.setState(ParticipantState.ADMITTED);
        participant.setAdmittedAt(Instant.now());
        participantRepository.save(participant);
    }

    private Instant expiryFor(Instant scheduledAt, Integer durationMinutes) {
        Duration length = durationMinutes == null
                ? Duration.ofHours(1)
                : Duration.ofMinutes(durationMinutes);
        return scheduledAt.plus(length).plus(SCHEDULED_MEETING_GRACE);
    }

    private String validTimezone(String zone) {
        if (zone == null || zone.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(zone).getId();
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown timezone: " + zone);
        }
    }

    private Meeting requireMeeting(String code) {
        return meetingRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meeting not found"));
    }

    private void requireHost(Meeting meeting, Long userId) {
        if (!meeting.getHostUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can do that");
        }
    }

    private MeetingSummaryDTO toSummary(Meeting meeting) {
        return new MeetingSummaryDTO(
                meeting.getId(),
                meeting.getCode(),
                meeting.getTitle(),
                meeting.getStatus(),
                meeting.getMarginId(),
                marginLookup.summaryOf(meeting.getMarginId()).name(),
                meeting.getHostUserId(),
                meeting.getScheduledAt(),
                meeting.getDurationMinutes(),
                meeting.getOrganizerTimezone(),
                meeting.getStartedAt(),
                meeting.getEndedAt(),
                inviteRepository.countByMeetingId(meeting.getId()));
    }

}
