package org.margin.server.presence.events;

import lombok.Getter;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserDisconnectedEvent extends ApplicationEvent {
    private final User user;

    public UserDisconnectedEvent(User user) {
        super(user);
        this.user = user;
    }
}