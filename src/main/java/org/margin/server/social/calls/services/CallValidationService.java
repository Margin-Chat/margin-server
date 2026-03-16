package org.margin.server.social.calls.services;

import org.margin.server.social.calls.exceptions.CallValidationException;
import org.margin.server.social.calls.models.Call;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;

@Service
public class CallValidationService {
    public void validateUserIsReceiver(Call call, User user) {
        validateUserIsInCall(call, user);
        if (!call.getReceiver().getId().equals(user.getId())) {
            throw new CallValidationException(call.getId(), "User is not the call receiver");
        }
    }

    public void validateUserIsSender(Call call, User user) {
        validateUserIsInCall(call, user);
        if (!call.getCaller().getId().equals(user.getId())) {
            throw new CallValidationException(call.getId(), "User is not the call caller");
        }
    }

    public void validateUserIsInCall(Call call, User user) {
        if (!call.getCaller().getId().equals(user.getId()) &&
                !call.getReceiver().getId().equals(user.getId())) {
            throw new CallValidationException(call.getId(), "User is not a participant in this call");
        }
    }
}