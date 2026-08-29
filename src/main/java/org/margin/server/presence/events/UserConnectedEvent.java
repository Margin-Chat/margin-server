package org.margin.server.presence.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserConnectedEvent extends ApplicationEvent {
    private final Long userId;

    public UserConnectedEvent(Long userId) {
        super(userId);
        this.userId = userId;
    }
}
