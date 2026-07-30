package org.margin.server.social.margin.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserInvitedToMarginEvent extends ApplicationEvent {
    private final Long invitedUserId;
    private final Long invitedByUserId;
    private final Long inviteId;
    private final String inviteCode;
    private final Long marginId;

    public UserInvitedToMarginEvent(Long invitedUserId,
                                    Long invitedByUserId,
                                    Long inviteId,
                                    String inviteCode,
                                    Long marginId) {
        super(inviteId);
        this.invitedUserId = invitedUserId;
        this.invitedByUserId = invitedByUserId;
        this.inviteId = inviteId;
        this.inviteCode = inviteCode;
        this.marginId = marginId;
    }
}