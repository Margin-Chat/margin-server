package org.margin.server.social.calls.events;

import lombok.Getter;
import org.margin.server.social.calls.models.CallCandidate;
import org.springframework.context.ApplicationEvent;

@Getter
public class CallCandidateForwardedEvent extends ApplicationEvent {
    private final Long recipientId;
    private final CallCandidate payload;

    public CallCandidateForwardedEvent(Long recipientId, CallCandidate payload) {
        super(recipientId);
        this.recipientId = recipientId;
        this.payload = payload;
    }
}