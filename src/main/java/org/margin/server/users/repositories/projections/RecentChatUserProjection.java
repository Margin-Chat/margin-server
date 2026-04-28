package org.margin.server.users.repositories.projections;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.users.models.User;

import java.time.Instant;

public record RecentChatUserProjection(Conversation conversation,
                                       User user,
                                       String lastMessage,
                                       Instant lastMessageTime,
                                       boolean lastMessageIncoming) {
}