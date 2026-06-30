package org.margin.server.social.conversation.models.dtos;

import java.time.Instant;
import java.util.List;

public record GroupConversationDTO(
        Long id,
        String type,
        Instant createdAt,
        String name,
        List<Long> memberIds,
        List<Long> pendingMemberIds,
        boolean encrypted
) implements ConversationDTO {
    public GroupConversationDTO(Long id, Instant createdAt, String name, List<Long> memberIds, List<Long> pendingMemberIds, boolean encrypted) {
        this(id, "GROUP", createdAt, name, memberIds, pendingMemberIds, encrypted);
    }
}
