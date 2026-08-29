package org.margin.server.presence.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserDisconnectedEvent extends ApplicationEvent {
    private final Long userId;

    public UserDisconnectedEvent(Long userId) {
        super(userId);
        this.userId = userId;
    }
}
