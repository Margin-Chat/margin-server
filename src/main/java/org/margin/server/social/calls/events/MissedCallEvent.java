package org.margin.server.social.calls.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class MissedCallEvent extends ApplicationEvent {
    private final Long recipientId;
    private final Long callerId;
    private final Long callId;

    public MissedCallEvent(Long recipientId, Long callerId, Long callId) {
        super(callId);
        this.recipientId = recipientId;
        this.callerId = callerId;
        this.callId = callId;
    }
}