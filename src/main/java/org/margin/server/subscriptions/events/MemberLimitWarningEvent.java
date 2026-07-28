package org.margin.server.subscriptions.events;

import lombok.Getter;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class MemberLimitWarningEvent extends ApplicationEvent {
    private final List<User> recipients;
    private final Long marginId;

    public MemberLimitWarningEvent(List<User> recipients, Long marginId) {
        super(marginId);
        this.recipients = recipients;
        this.marginId = marginId;
    }
}