package org.margin.server.social.messages.models.dtos;

public record UnreadCountDTO (
        Long conversationId,
        Long fromUserId,
        Long unreadDmsCount,
        Long unreadChannelsCount
) {}