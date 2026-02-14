package org.margin.server.social.messages.models.dtos;

import java.time.LocalDateTime;

public record DirectConversationDTO(
        Long id,
        String type,
        LocalDateTime createdAt,
        Long otherUserId
) implements ConversationDTO {
    public DirectConversationDTO(Long id, LocalDateTime createdAt, Long otherUserId) {
        this(id, "DIRECT", createdAt, otherUserId);
    }
}