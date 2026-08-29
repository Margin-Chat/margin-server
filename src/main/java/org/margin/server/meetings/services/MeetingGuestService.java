package org.margin.server.meetings.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.meetings.entities.Meeting;
import org.margin.server.meetings.entities.MeetingParticipant;
import org.margin.server.meetings.models.MeetingRole;
import org.margin.server.meetings.models.ParticipantState;
import org.margin.server.meetings.models.dtos.ClaimGuestRequest;
import org.margin.server.meetings.models.dtos.GuestSessionResponse;
import org.margin.server.meetings.models.dtos.MeetingPreviewDTO;
import org.margin.server.meetings.repositories.MeetingParticipantRepository;
import org.margin.server.meetings.repositories.MeetingRepository;
import org.margin.server.meetings.security.MeetingGuestPrincipal;
import org.margin.server.social.api.MarginLookup;
import org.margin.server.users.api.UserAccountCommands;
import org.margin.server.users.api.UserLookup;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
public class MeetingGuestService {

    private static final Duration GUEST_RETENTION = Duration.ofHours(24);
    private static final int MAX_DISPLAY_NAME = 50;

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final MeetingGuestTokenService guestTokenService;
    private final UserAccountCommands userAccountCommands;
    private final UserLookup userLookup;
    private final MarginLookup marginLookup;
    private final PasswordEncoder passwordEncoder;

    public MeetingGuestService(MeetingRepository meetingRepository,
                               MeetingParticipantRepository participantRepository,
                               MeetingGuestTokenService guestTokenService,
                               UserAccountCommands userAccountCommands,
                               UserLookup userLookup,
                               MarginLookup marginLookup,
                               PasswordEncoder passwordEncoder) {
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
        this.guestTokenService = guestTokenService;
        this.userAccountCommands = userAccountCommands;
        this.userLookup = userLookup;
        this.marginLookup = marginLookup;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public MeetingPreviewDTO preview(String code) {
        Meeting meeting = requireMeeting(code);

        return new MeetingPreviewDTO(
                meeting.getCode(),
                meeting.getTitle(),
                marginLookup.summaryOf(meeting.getMarginId()).name(),
                userLookup.summaryOf(meeting.getHostUserId()).displayName(),
                meeting.isRequireAdmission(),
                meeting.isJoinable());
    }

    @Transactional
    public GuestSessionResponse createGuestSession(String code, String requestedName) {
        Meeting meeting = requireMeeting(code);
        if (!meeting.isJoinable()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Meeting is no longer available");
        }

        String displayName = sanitiseName(requestedName);
        Instant expiresAt = meeting.getExpiresAt().plus(GUEST_RETENTION);
        Long guestId = userAccountCommands.createGuest(displayName, expiresAt);

        MeetingParticipant participant = new MeetingParticipant();
        participant.setMeetingId(meeting.getId());
        participant.setUserId(guestId);
        participant.setDisplayName(displayName);
        participant.setGuest(true);
        participant.setRole(MeetingRole.PARTICIPANT);
        participant.setState(meeting.isRequireAdmission() ? ParticipantState.KNOCKING : ParticipantState.ADMITTED);
        if (meeting.isRequireAdmission()) {
            participant.setKnockedAt(Instant.now());
        } else {
            participant.setAdmittedAt(Instant.now());
        }
        participantRepository.save(participant);

        String token = guestTokenService.generate(
                guestId, meeting.getId(), meeting.getCode(), displayName, meeting.getExpiresAt());

        log.info("Guest {} created a session for meeting {}", guestId, code);
        return new GuestSessionResponse(token, guestId, displayName, meeting.isRequireAdmission());
    }

    @Transactional(readOnly = true)
    public GuestSessionResponse resume(MeetingGuestPrincipal guest) {
        Meeting meeting = meetingRepository.findById(guest.meetingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meeting not found"));

        MeetingParticipant participant = participantRepository
                .findByMeetingIdAndUserId(meeting.getId(), guest.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not in this meeting"));

        if (participant.getState() == ParticipantState.DENIED
                || participant.getState() == ParticipantState.REMOVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "NOT_ADMITTED");
        }

        boolean admitted = participant.getState() == ParticipantState.ADMITTED
                || participant.getState() == ParticipantState.JOINED;

        return new GuestSessionResponse(null, guest.userId(), participant.getDisplayName(), !admitted);
    }

    @Transactional
    public void claim(MeetingGuestPrincipal guest, ClaimGuestRequest request) {
        if (request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required");
        }
        if (!userLookup.isGuest(guest.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "ALREADY_CLAIMED");
        }
        if (userAccountCommands.emailIsTaken(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_IN_USE");
        }

        try {
            userAccountCommands.promoteGuest(
                    guest.userId(),
                    request.email(),
                    passwordEncoder.encode(request.password()),
                    request.publicKey(),
                    request.encryptedPrivateKey(),
                    request.salt(),
                    request.iv());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_IN_USE");
        }

        log.info("Guest {} claimed their profile", guest.userId());
    }

    private String sanitiseName(String requested) {
        String name = requested == null ? "" : requested.trim();
        if (name.isBlank()) {
            name = "Guest";
        }
        return name.length() > MAX_DISPLAY_NAME ? name.substring(0, MAX_DISPLAY_NAME) : name;
    }

    private Meeting requireMeeting(String code) {
        return meetingRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meeting not found"));
    }
}
