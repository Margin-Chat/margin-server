package org.margin.server.social.calls.events;

import lombok.Getter;
import org.margin.server.social.calls.models.CallSessionDescription;
import org.springframework.context.ApplicationEvent;

@Getter
public class CallAnsweredEvent extends ApplicationEvent {
    private final Long recipientId;
    private final Long callerId;
    private final Long callId;
    private final CallSessionDescription callSessionDescription;

    public CallAnsweredEvent(Long recipientId,
                             Long callerId,
                             Long callId,
                             CallSessionDescription callSessionDescription) {
        super(callId);
        this.recipientId = recipientId;
        this.callerId = callerId;
        this.callId = callId;
        this.callSessionDescription = callSessionDescription;

    }
}