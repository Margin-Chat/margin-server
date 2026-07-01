package org.margin.server.websocket.models.payloads;

import org.margin.server.users.models.dtos.UserDTO;

public record ConversationDeclinedPayload(Long conversationId, UserDTO declinedBy) {
}
