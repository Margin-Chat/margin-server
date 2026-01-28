package org.margin.server.social.communication.exceptions;

import lombok.Getter;

@Getter
public class CallNotFoundException extends RuntimeException {

    private final Long callId;

    public CallNotFoundException(Long callId) {
        super("Call not found: " + callId);
        this.callId = callId;
    }

}