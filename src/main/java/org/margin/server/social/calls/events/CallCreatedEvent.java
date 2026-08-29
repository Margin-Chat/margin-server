package org.margin.server.social.calls.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CallCreatedEvent extends ApplicationEvent {
    private final Long callerId;
    private final Long callId;

    public CallCreatedEvent(Long callerId,
                            Long callId) {
        super(callId);
        this.callerId = callerId;
        this.callId = callId;
    }
}