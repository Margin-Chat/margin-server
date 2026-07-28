package org.margin.server.social.calls.events;

import lombok.Getter;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

@Getter
public class MissedCallEvent extends ApplicationEvent {
    private final User recipient;
    private final User caller;
    private final Long callId;

    public MissedCallEvent(User recipient, User caller, Long callId) {
        super(callId);
        this.recipient = recipient;
        this.caller = caller;
        this.callId = callId;
    }
}