package org.margin.server.social.conversation.models.dtos;

import org.margin.server.social.messages.models.dtos.MessageDTO;

import java.util.List;

public record GetConversationMessagesResponse(List<MessageDTO> messages, ConversationDTO conversation) {
}
