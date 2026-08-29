package org.margin.server.social.conversation.models.dtos;

import org.margin.server.social.conversation.models.ConversationInviteStatus;

import java.time.Instant;

public record DirectConversationDTO(
        Long id,
        String type,
        Instant createdAt,
        Long otherUserId,
        Instant otherUserReadAt,
        ConversationInviteStatus inviteStatus,
        boolean encrypted
) implements ConversationDTO {
    public DirectConversationDTO(Long id, Instant createdAt, Long otherUserId, Instant otherUserReadAt,
                                 ConversationInviteStatus inviteStatus, boolean encrypted) {
        this(id, "DIRECT", createdAt, otherUserId, otherUserReadAt, inviteStatus, encrypted);
    }
}
