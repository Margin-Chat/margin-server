package org.margin.server.users.repositories.projections;

import org.margin.server.users.models.User;

import java.time.LocalDateTime;

public record RecentChatUserProjection(User user,
                                       String lastMessage,
                                       LocalDateTime lastMessageTime,
                                       boolean lastMessageIncoming) {
}