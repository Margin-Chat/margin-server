package org.margin.server.social.conversation.models.dtos;


import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;

public record RecentChatUsersDTO(
        ConversationDTO conversation,
        UserDTO user,
        String lastMessage,
        Instant lastMessageTime,
        boolean lastMessageIncoming
) {
}