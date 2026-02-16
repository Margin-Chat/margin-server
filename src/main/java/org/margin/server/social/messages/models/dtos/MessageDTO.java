package org.margin.server.social.messages.models.dtos;

import org.margin.server.social.conversation.ConversationType;
import org.margin.server.social.messages.models.Message;

import java.time.Instant;
import java.time.LocalDateTime;

public record MessageDTO(
        Long id,
        Long conversationId,
        ConversationType conversationType,
        Long fromUserId,
        String fromUsername,
        String content,
        boolean isEdited,
        Instant createdAt
) {
    public MessageDTO(Message message, ConversationType conversationType) {
        this(
                message.getId(),
                message.getConversation().getId(),
                conversationType,
                message.getFromUser().getId(),
                message.getFromUser().getUsername(),
                message.getMessage(),
                message.getIsEdited(),
                message.getCreatedAt().toInstant(java.time.ZoneOffset.UTC)
        );
    }
}