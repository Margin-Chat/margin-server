package org.margin.server.social.announcements.models.dtos;

import org.margin.server.users.models.dtos.UserDTO;

import java.time.LocalDateTime;

public record AnnouncementDTO(
        Long announcementId,
        Long marginId,
        UserDTO author,
        String title,
        String content,
        LocalDateTime createdAt
) {
}
