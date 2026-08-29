package org.margin.server.meetings.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.meetings.api.MeetingAdmissionCommands;
import org.margin.server.meetings.entities.Meeting;
import org.margin.server.meetings.entities.MeetingParticipant;
import org.margin.server.meetings.events.MeetingAdmittedEvent;
import org.margin.server.meetings.events.MeetingDeniedEvent;
import org.margin.server.meetings.events.MeetingKnockEvent;
import org.margin.server.meetings.events.MeetingRingEvent;
import org.margin.server.meetings.models.MeetingInvitePayload;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.shared.notifications.NotificationType;
import org.margin.server.social.api.MarginLookup;
import org.margin.server.users.api.UserLookup;
import org.margin.server.meetings.events.MeetingParticipantRemovedEvent;
import org.margin.server.meetings.models.MeetingRole;
import org.margin.server.meetings.models.ParticipantState;
import org.margin.server.meetings.models.dtos.MeetingParticipantDTO;
import org.margin.server.meetings.repositories.MeetingParticipantRepository;
import org.margin.server.meetings.repositories.MeetingRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class MeetingAdmissionService implements MeetingAdmissionCommands {

    private static final Set<MeetingRole> ADMITTING_ROLES = EnumSet.of(MeetingRole.HOST, MeetingRole.COHOST);

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MarginAccessChecker marginAccessChecker;
    private final MarginLookup marginLookup;
    private final UserLookup userLookup;
    private final NotificationService notifications;

    public MeetingAdmissionService(MeetingRepository meetingRepository,
                                   MeetingParticipantRepository participantRepository,
                                   ApplicationEventPublisher eventPublisher,
                                   MarginAccessChecker marginAccessChecker,
                                   MarginLookup marginLookup,
                                   UserLookup userLookup,
                                   NotificationService notifications) {
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
        this.eventPublisher = eventPublisher;
        this.marginAccessChecker = marginAccessChecker;
        this.marginLookup = marginLookup;
        this.userLookup = userLookup;
        this.notifications = notifications;
    }

    @Override
    @Transactional
    public void knock(Long meetingId, Long guestUserId) {
        MeetingParticipant participant = requireParticipant(meetingId, guestUserId);

        if (participant.getState() == ParticipantState.DENIED
                || participant.getState() == ParticipantState.REMOVED) {
            return;
        }

        if (participant.getState() == ParticipantState.ADMITTED
                || participant.getState() == ParticipantState.JOINED) {
            eventPublisher.publishEvent(new MeetingAdmittedEvent(meetingId, guestUserId));
            return;
        }

        participant.setState(ParticipantState.KNOCKING);
        participant.setKnockedAt(Instant.now());
        participantRepository.save(participant);

        eventPublisher.publishEvent(new MeetingKnockEvent(
                meetingId, guestUserId, participant.getDisplayName(), admitterUserIds(meetingId)));
    }

    @Override
    @Transactional
    public void leaveLobby(Long meetingId, Long guestUserId) {
        participantRepository.findByMeetingIdAndUserId(meetingId, guestUserId).ifPresent(p -> {
            if (p.getState() == ParticipantState.KNOCKING) {
                p.setState(ParticipantState.LEFT);
                p.setLeftAt(Instant.now());
                participantRepository.save(p);
            }
        });
    }

    @Override
    @Transactional
    public void ring(String code, Long inviterId, Long recipientId) {
        Meeting meeting = requireMeeting(code);

        if (!meeting.isJoinable()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Meeting is no longer available");
        }
        marginAccessChecker.requireMarginMember(inviterId, meeting.getMarginId());
        marginAccessChecker.requireMarginMember(recipientId, meeting.getMarginId());
        if (userLookup.isGuest(recipientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot ring a guest");
        }

        MeetingInvitePayload payload = new MeetingInvitePayload(
                meeting.getCode(),
                meeting.getTitle(),
                marginLookup.summaryOf(meeting.getMarginId()).name(),
                userLookup.dtoOf(inviterId));

        eventPublisher.publishEvent(new MeetingRingEvent(recipientId, payload));
        notifications.createForUsers(List.of(recipientId), inviterId,
                NotificationType.MEETING_INVITE, meeting.getId(), meeting.getMarginId());
    }

    @Transactional
    public void admit(String code, Long actingUserId, Long participantId) {
        Meeting meeting = requireMeeting(code);
        requireAdmitter(meeting.getId(), actingUserId);

        MeetingParticipant participant = requireParticipantById(meeting.getId(), participantId);

        if (participant.getState() == ParticipantState.ADMITTED
                || participant.getState() == ParticipantState.JOINED) {
            return;
        }
        if (participant.getState() != ParticipantState.KNOCKING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Participant is not waiting");
        }

        participant.setState(ParticipantState.ADMITTED);
        participant.setAdmittedAt(Instant.now());
        participant.setAdmittedBy(actingUserId);
        participantRepository.save(participant);

        eventPublisher.publishEvent(new MeetingAdmittedEvent(meeting.getId(), participant.getUserId()));
        log.info("Participant {} admitted to meeting {} by {}", participantId, code, actingUserId);
    }

    @Transactional
    public void deny(String code, Long actingUserId, Long participantId) {
        Meeting meeting = requireMeeting(code);
        requireAdmitter(meeting.getId(), actingUserId);

        MeetingParticipant participant = requireParticipantById(meeting.getId(), participantId);
        participant.setState(ParticipantState.DENIED);
        participantRepository.save(participant);

        eventPublisher.publishEvent(new MeetingDeniedEvent(meeting.getId(), participant.getUserId()));
    }

    @Transactional
    public void remove(String code, Long actingUserId, Long participantId) {
        Meeting meeting = requireMeeting(code);
        requireAdmitter(meeting.getId(), actingUserId);

        MeetingParticipant participant = requireParticipantById(meeting.getId(), participantId);
        if (participant.getUserId() != null && participant.getUserId().equals(meeting.getHostUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The host cannot be removed");
        }

        participant.setState(ParticipantState.REMOVED);
        participant.setLeftAt(Instant.now());
        participantRepository.save(participant);

        eventPublisher.publishEvent(new MeetingParticipantRemovedEvent(meeting.getId(), participant.getUserId()));
    }

    @Transactional(readOnly = true)
    public List<MeetingParticipantDTO> waiting(String code, Long actingUserId) {
        Meeting meeting = requireMeeting(code);
        requireAdmitter(meeting.getId(), actingUserId);

        return participantRepository.findByMeetingIdAndState(meeting.getId(), ParticipantState.KNOCKING)
                .stream()
                .map(p -> new MeetingParticipantDTO(
                        p.getId(), p.getUserId(), p.getDisplayName(), p.isGuest(),
                        p.getRole(), p.getState(), false))
                .toList();
    }

    @Transactional
    public void transferHostIfVacant(Long meetingId) {
        List<MeetingParticipant> participants = participantRepository.findByMeetingId(meetingId);

        boolean hostPresent = participants.stream()
                .anyMatch(p -> p.getRole() == MeetingRole.HOST && isActive(p));
        if (hostPresent) {
            return;
        }

        participants.stream()
                .filter(p -> !p.isGuest() && isActive(p))
                .min((a, b) -> {
                    Instant left = a.getJoinedAt() == null ? Instant.MAX : a.getJoinedAt();
                    Instant right = b.getJoinedAt() == null ? Instant.MAX : b.getJoinedAt();
                    return left.compareTo(right);
                })
                .ifPresent(next -> {
                    next.setRole(MeetingRole.HOST);
                    participantRepository.save(next);
                    log.info("Host of meeting {} transferred to userId {}", meetingId, next.getUserId());
                });
    }

    private boolean isActive(MeetingParticipant p) {
        return p.getState() == ParticipantState.ADMITTED || p.getState() == ParticipantState.JOINED;
    }

    private List<Long> admitterUserIds(Long meetingId) {
        return participantRepository.findByMeetingId(meetingId).stream()
                .filter(p -> ADMITTING_ROLES.contains(p.getRole()))
                .filter(p -> !p.isGuest())
                .map(MeetingParticipant::getUserId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private void requireAdmitter(Long meetingId, Long userId) {
        MeetingParticipant participant = participantRepository.findByMeetingIdAndUserId(meetingId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not in this meeting"));

        if (!ADMITTING_ROLES.contains(participant.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a host can admit");
        }
    }

    private Meeting requireMeeting(String code) {
        return meetingRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meeting not found"));
    }

    private MeetingParticipant requireParticipant(Long meetingId, Long userId) {
        return participantRepository.findByMeetingIdAndUserId(meetingId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not in this meeting"));
    }

    private MeetingParticipant requireParticipantById(Long meetingId, Long participantId) {
        MeetingParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found"));
        if (!participant.getMeetingId().equals(meetingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found");
        }
        return participant;
    }
}
