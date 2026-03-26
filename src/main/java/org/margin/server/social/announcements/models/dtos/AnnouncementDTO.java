package org.margin.server.social.announcements.models.dtos;

import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;

public record AnnouncementDTO(
        Long announcementId,
        Long marginId,
        UserDTO author,
        String title,
        String content,
        Instant createdAt
) {
}
