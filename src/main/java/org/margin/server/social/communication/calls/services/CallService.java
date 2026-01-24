package org.margin.server.social.communication.calls.services;

import org.margin.server.social.communication.calls.models.Call;
import org.margin.server.social.communication.calls.models.CallStatus;
import org.margin.server.social.communication.calls.models.CallType;
import org.margin.server.social.communication.calls.repositories.CallRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CallService {
    private final CallRepository callRepository;

    public CallService(CallRepository callRepository) {
        this.callRepository = callRepository;
    }

    public Long createNewCall(Long fromUser, Long toUser, CallStatus callStatus, CallType callType, String sdp) {
        Call call = new Call(
                fromUser,
                toUser,
                callStatus,
                callType,
                sdp
        );
        callRepository.save(call);
        return call.getId();
    }

    public void updateCallStatus(Long callId, CallStatus callStatus) {
        Call call = callRepository.getCallByById(callId);
        call.setStatus(callStatus);
        callRepository.save(call);
    }

    public void endCall(Long callId, Integer duration) {
        Call call = callRepository.getCallByById(callId);
        call.setStatus(CallStatus.ENDED);
        call.setEndedAt(LocalDateTime.now());
        call.setDurationSeconds(duration);
        callRepository.save(call);
    }
}
