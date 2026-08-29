package org.margin.server.social.conversation.models.projections;

import org.margin.server.social.conversation.models.Conversation;

import java.time.Instant;

public record RecentChatUserProjection(Conversation conversation,
                                       Long userId,
                                       String lastMessage,
                                       Instant lastMessageTime,
                                       boolean lastMessageIncoming) {
}