package org.margin.server.users.models.dtos;

import org.margin.server.users.models.User;

import java.time.LocalDateTime;

public record RecentChatUsersDTO(
        UserDTO user,
        String lastMessage,
        LocalDateTime lastMessageTime,
        boolean lastMessageIncoming
) {
    public RecentChatUsersDTO(User user, String lastMessage, LocalDateTime lastMessageTime,  boolean lastMessageIncoming) {
        this(new UserDTO(user), lastMessage, lastMessageTime, lastMessageIncoming);
    }
}