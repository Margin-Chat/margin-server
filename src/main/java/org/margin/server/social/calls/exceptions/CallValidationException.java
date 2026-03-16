package org.margin.server.social.calls.exceptions;

public class CallValidationException extends RuntimeException {
    public CallValidationException(Long callId, String message) {
        super("Call " + callId + ": " + message);
    }
}