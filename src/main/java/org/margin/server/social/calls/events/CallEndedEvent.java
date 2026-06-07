package org.margin.server.social.calls.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CallEndedEvent extends ApplicationEvent {
    private final Long recipientId;

    public CallEndedEvent(Long recipientId) {
        super(recipientId);
        this.recipientId = recipientId;
    }
}