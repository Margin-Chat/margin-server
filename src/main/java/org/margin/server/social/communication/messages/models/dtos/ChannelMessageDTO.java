package org.margin.server.social.communication.messages.models.dtos;

import org.margin.server.social.communication.messages.models.ChannelMessage;

import java.util.Date;

public record ChannelMessageDTO(
        Long id,
        String message,
        Long fromUserId,
        String fromUsername,
        Date createdAt,
        Long channelId,
        Boolean isEdited
) {
    public static ChannelMessageDTO fromEntity(ChannelMessage entity, String fromUsername) {
        return new ChannelMessageDTO(
                entity.getId(),
                entity.getMessage(),
                entity.getFromUser().getId(),
                fromUsername,
                entity.getCreatedAt(),
                entity.getChannel().getId(),
                entity.getIsEdited()
        );
    }
}