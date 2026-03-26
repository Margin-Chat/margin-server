package org.margin.server.social.conversation.models.dtos;

import java.time.Instant;

public record ChannelConversationDTO(
        Long id,
        String type,
        Instant createdAt,
        Long channelId,
        String name
) implements ConversationDTO {
    public ChannelConversationDTO(Long id, Instant createdAt, Long channelId, String name) {
        this(id, "CHANNEL", createdAt, channelId, name);
    }
}