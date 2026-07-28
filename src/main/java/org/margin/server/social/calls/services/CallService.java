package org.margin.server.social.calls.services;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.calls.events.MissedCallEvent;
import org.margin.server.social.calls.events.CallEndedEvent;
import org.margin.server.social.calls.events.CallOfferedEvent;
import org.margin.server.social.calls.events.CallResponseForwardedEvent;
import org.margin.server.social.calls.exceptions.CallNotFoundException;
import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.social.calls.models.CallType;
import org.margin.server.social.calls.repositories.CallRepository;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.social.calls.models.CallSessionDescription;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class CallService {
    private final CallRepository callRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserService userService;
    private final CallValidationService callValidationService;

    public CallService(CallRepository callRepository, ApplicationEventPublisher eventPublisher, UserService userService, CallValidationService callValidationService) {
        this.callRepository = callRepository;
        this.eventPublisher = eventPublisher;
        this.userService = userService;
        this.callValidationService = callValidationService;
    }

    @Transactional
    public Call createCall(User fromUserId, Long toUserId, CallStatus status, CallType type, String sdp) {
        Call call = new Call(
                fromUserId,
                userService.getById(toUserId),
                status,
                type);
        Call saved = callRepository.save(call);

        log.debug("Call created: {} -> {} (type: {}, id: {})",
                fromUserId, toUserId, type, saved.getId());

        eventPublisher.publishEvent(new CallOfferedEvent(
                toUserId,
                fromUserId.getId(),
                call.getId(),
                sdp,
                CallType.AUDIO));

        return saved;
    }

    @Transactional
    public void updateCallStatus(Long callId, CallStatus status) {
        Call call = getById(callId);
        call.setStatus(status);
        callRepository.save(call);

        log.debug("Call {} status updated to {}", callId, status);
    }

    @Transactional
    public void acceptCall(Long callId, Long callerId, Long recipientId, CallSessionDescription response) {
        updateCallStatus(callId, CallStatus.ACCEPTED);
        eventPublisher.publishEvent(new CallResponseForwardedEvent(recipientId, callId, callerId, response));
    }

    @Transactional
    public void endCall(User user, Long callId, Integer durationSeconds, Long recipientId) {
        Call call = getById(callId);
        callValidationService.validateUserIsInCall(call, user);

        call.setStatus(CallStatus.ENDED);
        call.setEndedAt(Instant.now());
        call.setDurationSeconds(durationSeconds);
        callRepository.save(call);

        log.debug("Call {} ended (duration: {}s)", call.getId(), durationSeconds);

        eventPublisher.publishEvent(new CallEndedEvent(recipientId, call.getId()));
    }

    @Transactional
    public void callNoAnswer(Long callId, User user, Long recipientId) {
        Call call = getById(callId);

        callValidationService.validateUserIsSender(call, user);
        User recepientUser = userService.getById(recipientId);

        call.setEndedAt(Instant.now());
        call.setDurationSeconds(0);
        call.setStatus(CallStatus.NO_RESPONSE);
        callRepository.save(call);

        log.debug("Call {} wasn't answered", call.getId());

        eventPublisher.publishEvent(new MissedCallEvent(recepientUser, user, call.getId()));
    }

    @Transactional
    public void rejectCall(Long callId, Long recipientId, User user) {
        Call call = getById(callId);
        callValidationService.validateUserIsReceiver(call, user);

        call.setStatus(CallStatus.REJECTED);
        call.setEndedAt(Instant.now());
        call.setDurationSeconds(0);
        callRepository.save(call);

        log.debug("Call {} was rejected", callId);

        eventPublisher.publishEvent(new CallEndedEvent(recipientId, call.getId()));
    }

    public Call getById(Long callId) {
        return callRepository.findById(callId)
                .orElseThrow(() -> new CallNotFoundException(callId));
    }
}
