package org.margin.server.social.calls.events;

import lombok.Getter;
import org.margin.server.websocket.models.payloads.IncomingCallCandidatePayload;
import org.springframework.context.ApplicationEvent;

@Getter
public class CallCandidateForwardedEvent extends ApplicationEvent {
    private final Long recipientId;
    private final IncomingCallCandidatePayload payload;

    public CallCandidateForwardedEvent(Long recipientId, IncomingCallCandidatePayload payload) {
        super(recipientId);
        this.recipientId = recipientId;
        this.payload = payload;
    }
}