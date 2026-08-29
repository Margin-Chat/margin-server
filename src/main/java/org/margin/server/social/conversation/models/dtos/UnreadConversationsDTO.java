package org.margin.server.social.conversation.models.dtos;

import java.util.List;

public record UnreadConversationsDTO(
        List<Long> directConversationIds,
        List<Long> groupConversationIds,
        List<ChannelUnread> channelUnreads,
        List<Long> threadConversationIds
) {
    public record ChannelUnread(Long conversationId, Long marginId) {
    }
}