package org.margin.server.social.messages.models.dtos;

public record UnreadCount(
        Long conversationId,
        Long unreadCount
) {}