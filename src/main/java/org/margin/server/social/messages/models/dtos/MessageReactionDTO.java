package org.margin.server.social.messages.models.dtos;

import org.margin.server.social.messages.models.MessageReaction;

public record MessageReactionDTO(
        Long id,
        Long messageId,
        Long conversationId,
        Long userId,
        String displayName,
        String emoji
) {
    public static MessageReactionDTO from(MessageReaction reaction, Long conversationId, String displayName) {
        return new MessageReactionDTO(
                reaction.getId(),
                reaction.getMessage().getId(),
                conversationId,
                reaction.getUserId(),
                displayName,
                reaction.getEmoji()
        );
    }
}
