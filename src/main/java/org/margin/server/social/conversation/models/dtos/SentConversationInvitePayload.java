package org.margin.server.social.conversation.models.dtos;

import org.margin.server.users.models.dtos.UserDTO;

public record SentConversationInvitePayload(ConversationDTO conversation, UserDTO toUser) {
}
