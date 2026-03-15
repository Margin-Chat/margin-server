package org.margin.server.social.calls.services;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.calls.exceptions.CallNotFoundException;
import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.social.calls.models.CallType;
import org.margin.server.social.calls.repositories.CallRepository;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class CallService {
    private final CallRepository callRepository;

    public CallService(CallRepository callRepository) {
        this.callRepository = callRepository;
    }

    @Transactional
    public Call createCall(User fromUserId, User toUserId, CallStatus status, CallType type, String sdp) {
        Call call = new Call(fromUserId, toUserId, status, type, sdp);
        Call saved = callRepository.save(call);

        log.debug("Call created: {} -> {} (type: {}, id: {})",
                fromUserId, toUserId, type, saved.getId());

        return saved;
    }

    @Transactional
    public void updateCallStatus(Long callId, CallStatus status) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CallNotFoundException(callId));

        call.setStatus(status);
        callRepository.save(call);

        log.debug("Call {} status updated to {}", callId, status);
    }

    @Transactional
    public void endCall(Long callId, Integer durationSeconds) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CallNotFoundException(callId));

        call.setStatus(CallStatus.ENDED);
        call.setEndedAt(LocalDateTime.now());
        call.setDurationSeconds(durationSeconds);
        callRepository.save(call);

        log.debug("Call {} ended (duration: {}s)", callId, durationSeconds);
    }

    @Transactional
    public void callNoAnswer(Long callId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new CallNotFoundException(callId));
        call.setEndedAt(LocalDateTime.now());
        call.setDurationSeconds(0);
        call.setStatus(CallStatus.NO_RESPONSE);
        callRepository.save(call);
    }
}
