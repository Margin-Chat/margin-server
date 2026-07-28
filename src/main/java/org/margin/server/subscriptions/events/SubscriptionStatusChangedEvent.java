package org.margin.server.subscriptions.events;

import lombok.Getter;
import org.margin.server.notifications.NotificationType;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionStatusChangedEvent extends ApplicationEvent {
    private final User owner;
    private final NotificationType type;
    private final Long marginId;

    public SubscriptionStatusChangedEvent(User owner, NotificationType type, Long marginId) {
        super(marginId);
        this.owner = owner;
        this.type = type;
        this.marginId = marginId;
    }
}