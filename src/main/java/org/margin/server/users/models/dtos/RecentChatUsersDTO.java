package org.margin.server.users.models.dtos;

import org.margin.server.social.conversation.models.dtos.ConversationDTO;

import java.time.Instant;

public record RecentChatUsersDTO(
        ConversationDTO conversation,
        UserDTO user,
        String lastMessage,
        Instant lastMessageTime,
        boolean lastMessageIncoming
) {
}