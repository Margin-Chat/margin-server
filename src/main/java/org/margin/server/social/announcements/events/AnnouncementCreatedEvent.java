package org.margin.server.social.announcements.events;

import lombok.Getter;
import org.margin.server.users.api.UserSummary;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class AnnouncementCreatedEvent extends ApplicationEvent {
    private final List<Long> memberIds;
    private final UserSummary author;
    private final Long announcementId;
    private final Long marginId;

    public AnnouncementCreatedEvent(List<Long> memberIds, UserSummary author, Long announcementId, Long marginId) {
        super(announcementId);
        this.memberIds = memberIds;
        this.author = author;
        this.announcementId = announcementId;
        this.marginId = marginId;
    }
}