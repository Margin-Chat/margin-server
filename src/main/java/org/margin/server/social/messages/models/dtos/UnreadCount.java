package org.margin.server.social.messages.models.dtos;

public record UnreadCount(
        Long conversationId,
        Long fromUserId,
        Long unreadDmsCount,
        Long unreadChannelsCount
) {}