package org.margin.server.social.conversation.models.dtos;

import java.time.LocalDateTime;
import java.util.List;

public record GroupConversationDTO(
        Long id,
        String type,
        LocalDateTime createdAt,
        String name,
        List<Long> memberIds
) implements ConversationDTO {
    public GroupConversationDTO(Long id, LocalDateTime createdAt, String name, List<Long> memberIds) {
        this(id, "GROUP", createdAt, name, memberIds);
    }
}