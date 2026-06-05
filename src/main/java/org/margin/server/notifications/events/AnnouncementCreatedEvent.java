package org.margin.server.notifications.events;

import lombok.Getter;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class AnnouncementCreatedEvent extends ApplicationEvent {
    private final List<User> members;
    private final User author;
    private final Long announcementId;
    private final Long marginId;

    public AnnouncementCreatedEvent(List<User> members, User author, Long announcementId, Long marginId) {
        super(announcementId);
        this.members = members;
        this.author = author;
        this.announcementId = announcementId;
        this.marginId = marginId;
    }
}