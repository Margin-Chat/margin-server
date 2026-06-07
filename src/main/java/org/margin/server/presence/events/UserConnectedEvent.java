package org.margin.server.presence.events;

import lombok.Getter;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserConnectedEvent extends ApplicationEvent {
    private final User user;

    public UserConnectedEvent(User user) {
        super(user);
        this.user = user;
    }
}