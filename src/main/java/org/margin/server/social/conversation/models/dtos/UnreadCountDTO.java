package org.margin.server.social.conversation.models.dtos;

public record UnreadCountDTO(
        Long conversationId,
        Long unreadDmsCount,
        Long unreadChannelsCount,
        Long marginId
) {
}