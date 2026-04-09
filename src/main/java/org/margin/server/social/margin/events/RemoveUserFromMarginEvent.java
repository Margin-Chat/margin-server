package org.margin.server.social.margin.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RemoveUserFromMarginEvent extends ApplicationEvent {
    private final Long marginId;
    private final Long userId;

    public RemoveUserFromMarginEvent(Long marginId, Long userId) {
        super(marginId);
        this.marginId = marginId;
        this.userId = userId;
    }

}
