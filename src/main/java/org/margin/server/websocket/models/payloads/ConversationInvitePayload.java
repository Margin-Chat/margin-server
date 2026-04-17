package org.margin.server.websocket.models.payloads;

import org.margin.server.social.conversation.models.dtos.DirectConversationDTO;
import org.margin.server.users.models.dtos.UserDTO;

public record ConversationInvitePayload(DirectConversationDTO conversation, UserDTO fromUser) {
}
