package org.margin.server.users.models.dtos;

import java.time.Instant;

public record RecentChatUsersDTO(
        UserDTO user,
        String lastMessage,
        Instant lastMessageTime,
        boolean lastMessageIncoming
) {
}