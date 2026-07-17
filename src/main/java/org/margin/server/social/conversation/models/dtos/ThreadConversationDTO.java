package org.margin.server.social.conversation.models.dtos;

import java.time.Instant;

public record ThreadConversationDTO(
        Long id,
        String type,
        Instant createdAt,
        Long parentConversationId,
        Long channelId,
        Long marginId,
        String title,
        boolean following
) implements ConversationDTO {
    public ThreadConversationDTO(Long id, Instant createdAt, Long parentConversationId,
                                 Long channelId, Long marginId, String title, boolean following) {
        this(id, "THREAD", createdAt, parentConversationId, channelId, marginId, title, following);
    }
}
