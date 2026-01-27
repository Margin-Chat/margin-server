package org.margin.server.social.communication.messages.models.dtos;

import org.margin.server.social.communication.messages.models.SpaceChannelMessage;

import java.util.Date;

public record SpaceChannelMessageDTO(
        Long id,
        String message,
        Long fromUserId,
        String fromUsername,
        Date createdAt,
        Long channelId,
        Boolean isEdited
) {
    public static SpaceChannelMessageDTO fromEntity(SpaceChannelMessage entity, String fromUsername) {
        return new SpaceChannelMessageDTO(
                entity.getId(),
                entity.getMessage(),
                entity.getFromUserId(),
                fromUsername,
                entity.getCreatedAt(),
                entity.getChannelId(),
                entity.getIsEdited()
        );
    }
}