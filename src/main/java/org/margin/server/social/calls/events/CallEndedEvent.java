package org.margin.server.social.calls.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CallEndedEvent extends ApplicationEvent {
    private final Long recipientId;
    private final Long callId;

    public CallEndedEvent(Long recipientId, Long callId) {
        super(recipientId);
        this.recipientId = recipientId;
        this.callId = callId;
    }
}