package org.margin.server.social.conversation.models.dtos;

import java.time.LocalDateTime;

public record ChannelConversationDTO(
        Long id,
        String type,
        LocalDateTime createdAt,
        Long channelId,
        String name
) implements ConversationDTO {
    public ChannelConversationDTO(Long id, LocalDateTime createdAt, Long channelId, String name) {
        this(id, "CHANNEL", createdAt, channelId, name);
    }
}