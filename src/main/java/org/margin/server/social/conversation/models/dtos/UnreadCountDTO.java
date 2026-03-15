package org.margin.server.social.conversation.models.dtos;

public record UnreadCountDTO(
        Long conversationId,
        Long fromUserId,
        Long unreadDmsCount,
        Long unreadChannelsCount
) {
}