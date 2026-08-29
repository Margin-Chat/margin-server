package org.margin.server.notifications.events;

import lombok.Getter;
import org.margin.server.notifications.Notification;
import org.springframework.context.ApplicationEvent;

@Getter
public class NotificationDeliveryEvent extends ApplicationEvent {
    private final Notification notification;

    public NotificationDeliveryEvent(Notification notification) {
        super(notification);
        this.notification = notification;
    }
}