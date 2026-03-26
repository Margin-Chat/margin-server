package org.margin.server.social.conversation.models.dtos;

import java.time.Instant;

public record DirectConversationDTO(
        Long id,
        String type,
        Instant createdAt,
        Long otherUserId
) implements ConversationDTO {
    public DirectConversationDTO(Long id, Instant createdAt, Long otherUserId) {
        this(id, "DIRECT", createdAt, otherUserId);
    }
}