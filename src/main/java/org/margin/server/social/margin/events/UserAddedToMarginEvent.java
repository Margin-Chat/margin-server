package org.margin.server.social.margin.events;

import lombok.Getter;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserAddedToMarginEvent extends ApplicationEvent {
    private final User addedUser;
    private final User addingUser;
    private final Long marginId;

    public UserAddedToMarginEvent(User addedUser, User addingUser, Long marginId) {
        super(marginId);
        this.addedUser = addedUser;
        this.addingUser = addingUser;
        this.marginId = marginId;
    }
}