package org.margin.server.social.messages.models.dtos;

import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.messages.models.Message;
import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;

public record MessageDTO(
        Long id,
        Long conversationId,
        ConversationType conversationType,
        UserDTO user,
        String content,
        boolean isEdited,
        Instant createdAt
) {
    public MessageDTO(Message message, ConversationType conversationType, boolean isUserOnline) {
        this(
                message.getId(),
                message.getConversation().getId(),
                conversationType,
                new UserDTO(message.getFromUser(), isUserOnline),
                message.getMessage(),
                message.getIsEdited(),
                message.getCreatedAt().toInstant(java.time.ZoneOffset.UTC)
        );
    }
}