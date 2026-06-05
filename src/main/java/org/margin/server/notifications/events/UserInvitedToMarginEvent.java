package org.margin.server.notifications.events;

import lombok.Getter;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserInvitedToMarginEvent extends ApplicationEvent {
    private final User invitedUser;
    private final User invitedBy;
    private final Long inviteId;
    private final Long marginId;

    public UserInvitedToMarginEvent(User invitedUser, User invitedBy, Long inviteId, Long marginId) {
        super(inviteId);
        this.invitedUser = invitedUser;
        this.invitedBy = invitedBy;
        this.inviteId = inviteId;
        this.marginId = marginId;
    }
}