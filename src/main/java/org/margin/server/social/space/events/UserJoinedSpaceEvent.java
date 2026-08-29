package org.margin.server.social.space.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class UserJoinedSpaceEvent extends ApplicationEvent {
    private final List<Long> spaceMemberIds;
    private final Long newMemberId;
    private final Long spaceId;

    public UserJoinedSpaceEvent(List<Long> spaceMemberIds, Long newMemberId, Long spaceId) {
        super(spaceId);
        this.spaceMemberIds = spaceMemberIds;
        this.newMemberId = newMemberId;
        this.spaceId = spaceId;
    }
}