package org.margin.server.social.margin.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserAddedToMarginEvent extends ApplicationEvent {
    private final Long addedUserId;
    private final Long addingUserId;
    private final Long marginId;

    public UserAddedToMarginEvent(Long addedUserId, Long addingUserId, Long marginId) {
        super(marginId);
        this.addedUserId = addedUserId;
        this.addingUserId = addingUserId;
        this.marginId = marginId;
    }
}