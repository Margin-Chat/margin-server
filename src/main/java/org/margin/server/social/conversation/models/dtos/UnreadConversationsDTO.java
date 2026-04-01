package org.margin.server.social.conversation.models.dtos;

import java.util.List;

public record UnreadConversationsDTO(
        List<Long> directConversationIds,
        List<ChannelUnread> channelUnreads
) {
    public record ChannelUnread(Long conversationId, Long marginId) {
    }
}