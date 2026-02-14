package org.margin.server.social.messages.models.dtos;

import org.margin.server.social.messages.models.Message;
import java.time.LocalDateTime;

public record MessageDTO(
        Long id,
        Long conversationId,
        Long fromUserId,
        String content,
        Boolean isEdited,
        LocalDateTime createdAt
) {
    public MessageDTO(Message message) {
        this(
                message.getId(),
                message.getConversation().getId(),
                message.getFromUser().getId(),
                message.getMessage(),
                message.getIsEdited(),
                message.getCreatedAt()
        );
    }
}