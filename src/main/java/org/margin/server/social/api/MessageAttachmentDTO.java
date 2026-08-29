package org.margin.server.social.api;

import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;

public record MessageAttachmentDTO(
        Long fileId,
        String scope,
        Long marginId,
        Long channelId,
        Long conversationId,
        String fileName,
        String contentType,
        long sizeBytes,
        String url,
        UserDTO uploadedBy,
        Instant uploadedAt
) {
}
