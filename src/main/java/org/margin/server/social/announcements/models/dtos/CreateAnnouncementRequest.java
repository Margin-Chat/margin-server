package org.margin.server.social.announcements.models.dtos;

public record CreateAnnouncementRequest(
        Long marginId,
        String title,
        String content
) {
}
