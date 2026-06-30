package org.margin.server.websocket.models.payloads;

import org.margin.server.social.conversation.models.dtos.ConversationDTO;
import org.margin.server.users.models.dtos.UserDTO;

public record ConversationInvitePayload(ConversationDTO conversation, UserDTO fromUser) {
}
