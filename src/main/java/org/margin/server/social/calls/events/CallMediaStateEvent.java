package org.margin.server.social.calls.events;

import lombok.Getter;
import org.margin.server.websocket.models.payloads.CallMediaStatePayload;
import org.springframework.context.ApplicationEvent;

@Getter
public class CallMediaStateEvent extends ApplicationEvent {
    private final Long recipientId;
    private final CallMediaStatePayload mediaStatePayload;

    public CallMediaStateEvent(Long recipientId,
                               CallMediaStatePayload mediaStatePayload) {
        super(recipientId);
        this.recipientId = recipientId;
        this.mediaStatePayload = mediaStatePayload;

    }
}