package org.margin.server.subscriptions.events;

import lombok.Getter;
import org.margin.server.shared.notifications.NotificationType;
import org.springframework.context.ApplicationEvent;

@Getter
public class SubscriptionStatusChangedEvent extends ApplicationEvent {
    private final Long ownerId;
    private final NotificationType type;
    private final Long marginId;

    public SubscriptionStatusChangedEvent(Long ownerId, NotificationType type, Long marginId) {
        super(marginId);
        this.ownerId = ownerId;
        this.type = type;
        this.marginId = marginId;
    }
}