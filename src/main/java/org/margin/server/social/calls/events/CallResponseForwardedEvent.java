package org.margin.server.social.calls.events;

import lombok.Getter;
import org.margin.server.social.calls.models.CallSessionDescription;
import org.springframework.context.ApplicationEvent;

@Getter
public class CallResponseForwardedEvent extends ApplicationEvent {
    private final Long recipientId;
    private final Long callId;
    private final Long callerId;
    private final CallSessionDescription response;

    public CallResponseForwardedEvent(Long recipientId, Long callId, Long callerId, CallSessionDescription response) {
        super(callId);
        this.recipientId = recipientId;
        this.callId = callId;
        this.callerId = callerId;
        this.response = response;
    }
}