package org.margin.server.social.calls.services;

import org.springframework.modulith.NamedInterface;

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
import org.margin.server.social.calls.models.CallSessionDescription;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

@NamedInterface("api")
@Slf4j
@Service
public class CallService {
    private final CallRepository callRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CallValidationService callValidationService;

    public CallService(CallRepository callRepository, ApplicationEventPublisher eventPublisher, CallValidationService callValidationService) {
        this.callRepository = callRepository;
        this.eventPublisher = eventPublisher;
        this.callValidationService = callValidationService;
    }

    @Transactional
    public Call createCall(Long fromUserId, Long toUserId, CallStatus status, CallType type, String sdp) {
        Call call = new Call(
                fromUserId,
                toUserId,
                status,
                type);
        Call saved = callRepository.save(call);

        log.debug("Call created: {} -> {} (type: {}, id: {})",
                fromUserId, toUserId, type, saved.getId());

        eventPublisher.publishEvent(new CallOfferedEvent(
                toUserId,
                fromUserId,
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
    public void endCall(Long userId, Long callId, Integer durationSeconds, Long recipientId) {
        Call call = getById(callId);
        callValidationService.validateUserIsInCall(call, userId);

        call.setStatus(CallStatus.ENDED);
        call.setEndedAt(Instant.now());
        call.setDurationSeconds(durationSeconds);
        callRepository.save(call);

        log.debug("Call {} ended (duration: {}s)", call.getId(), durationSeconds);

        eventPublisher.publishEvent(new CallEndedEvent(recipientId, call.getId()));
    }

    @Transactional
    public void callNoAnswer(Long callId, Long userId, Long recipientId) {
        Call call = getById(callId);

        callValidationService.validateUserIsSender(call, userId);

        call.setEndedAt(Instant.now());
        call.setDurationSeconds(0);
        call.setStatus(CallStatus.NO_RESPONSE);
        callRepository.save(call);

        log.debug("Call {} wasn't answered", call.getId());

        eventPublisher.publishEvent(new MissedCallEvent(recipientId, userId, call.getId()));
    }

    @Transactional
    public void rejectCall(Long callId, Long recipientId, Long userId) {
        Call call = getById(callId);
        callValidationService.validateUserIsReceiver(call, userId);

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
