package org.margin.server.social.calls.services;

import org.margin.server.social.calls.exceptions.CallValidationException;
import org.margin.server.social.calls.models.Call;
import org.springframework.stereotype.Service;

@Service
public class CallValidationService {
    public void validateUserIsReceiver(Call call, Long userId) {
        validateUserIsInCall(call, userId);
        if (!call.getReceiverId().equals(userId)) {
            throw new CallValidationException(call.getId(), "User is not the call receiver");
        }
    }

    public void validateUserIsSender(Call call, Long userId) {
        validateUserIsInCall(call, userId);
        if (!call.getCallerId().equals(userId)) {
            throw new CallValidationException(call.getId(), "User is not the call caller");
        }
    }

    public void validateUserIsInCall(Call call, Long userId) {
        if (!call.getCallerId().equals(userId) &&
                !call.getReceiverId().equals(userId)) {
            throw new CallValidationException(call.getId(), "User is not a participant in this call");
        }
    }
}
