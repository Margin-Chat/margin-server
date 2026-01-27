package org.margin.server.social.communication.messages.models.dtos;

import org.margin.server.social.communication.messages.models.DirectMessage;

import java.util.Date;

public record DirectMessageDTO(
        Long id,
        String message,
        Long fromUserId,
        Long toUserId,
        Boolean isRead,
        Boolean isEdited,
        Date createdAt
) {
    public static DirectMessageDTO fromEntity(DirectMessage entity) {
        return new DirectMessageDTO(
                entity.getId(),
                entity.getMessage(),
                entity.getFromUserId(),
                entity.getToUserId(),
                entity.getIsRead(),
                entity.getIsEdited(),
                entity.getCreatedAt()
        );
    }
}