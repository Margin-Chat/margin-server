package org.margin.server.social.calls.events;

import lombok.Getter;
import org.margin.server.social.calls.models.CallType;
import org.springframework.context.ApplicationEvent;

@Getter
public class CallOfferedEvent extends ApplicationEvent {
    private final Long recipientId;
    private final Long callerId;
    private final Long callId;
    private final String sdp;
    private final CallType callType;

    public CallOfferedEvent(Long recipientId,
                            Long callerId,
                            Long callId,
                            String sdp,
                            CallType callType) {
        super(callId);
        this.recipientId = recipientId;
        this.callerId = callerId;
        this.callId = callId;
        this.sdp = sdp;
        this.callType = callType;
    }
}
