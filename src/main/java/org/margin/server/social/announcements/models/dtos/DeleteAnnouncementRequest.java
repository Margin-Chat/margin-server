package org.margin.server.social.announcements.models.dtos;

public record DeleteAnnouncementRequest(
        Long marginId,
        Long announcementId
) {
}
