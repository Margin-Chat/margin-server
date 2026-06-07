package org.margin.server.social.space.events;

import lombok.Getter;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class UserJoinedSpaceEvent extends ApplicationEvent {
    private final List<User> spaceMembers;
    private final User newMember;
    private final Long spaceId;

    public UserJoinedSpaceEvent(List<User> spaceMembers, User newMember, Long spaceId) {
        super(spaceId);
        this.spaceMembers = spaceMembers;
        this.newMember = newMember;
        this.spaceId = spaceId;
    }
}