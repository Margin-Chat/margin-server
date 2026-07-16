package org.margin.server.social.conversation.models.dtos;

import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;

public record ThreadSummaryDTO(
        ThreadConversationDTO thread,
        UserDTO author,
        String bodyExcerpt,
        long replyCount,
        Instant lastReplyAt,
        boolean unread
) {
}
