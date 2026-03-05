package org.margin.server.users.models.dtos;

import java.time.LocalDateTime;

public record RecentChatUsersDTO(
        UserDTO user,
        String lastMessage,
        LocalDateTime lastMessageTime,
        boolean lastMessageIncoming
) {}