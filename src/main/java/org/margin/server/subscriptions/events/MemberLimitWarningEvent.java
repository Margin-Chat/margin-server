package org.margin.server.subscriptions.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class MemberLimitWarningEvent extends ApplicationEvent {
    private final List<Long> recipientIds;
    private final Long marginId;

    public MemberLimitWarningEvent(List<Long> recipientIds, Long marginId) {
        super(marginId);
        this.recipientIds = recipientIds;
        this.marginId = marginId;
    }
}