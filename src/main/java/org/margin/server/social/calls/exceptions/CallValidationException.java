package org.margin.server.social.calls.exceptions;

import org.margin.server.shared.exceptions.DomainException;
import org.springframework.http.HttpStatus;

public class CallValidationException extends DomainException {
    public CallValidationException(Long callId, String message) {
        super(HttpStatus.FORBIDDEN, "Call " + callId + ": " + message);
    }
}
