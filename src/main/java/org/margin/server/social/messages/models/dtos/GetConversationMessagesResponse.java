package org.margin.server.social.messages.models.dtos;

import java.util.List;

public record GetConversationMessagesResponse(List<MessageDTO> messages, ConversationDTO conversation) {
}
