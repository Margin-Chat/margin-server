package org.margin.server.social.calls.events;

import lombok.Getter;
import org.margin.server.social.calls.models.CallMediaState;
import org.springframework.context.ApplicationEvent;

@Getter
public class CallMediaStateEvent extends ApplicationEvent {
    private final Long recipientId;
    private final CallMediaState mediaStatePayload;

    public CallMediaStateEvent(Long recipientId,
                               CallMediaState mediaStatePayload) {
        super(recipientId);
        this.recipientId = recipientId;
        this.mediaStatePayload = mediaStatePayload;

    }
}