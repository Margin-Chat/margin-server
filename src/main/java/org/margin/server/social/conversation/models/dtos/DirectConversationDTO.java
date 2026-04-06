package org.margin.server.social.conversation.models.dtos;

import java.time.Instant;

public record DirectConversationDTO(
        Long id,
        String type,
        Instant createdAt,
        Long otherUserId,
        Instant otherUserReadAt
) implements ConversationDTO {
    public DirectConversationDTO(Long id, Instant createdAt, Long otherUserId, Instant otherUserReadAt) {
        this(id, "DIRECT", createdAt, otherUserId, otherUserReadAt);
    }
}