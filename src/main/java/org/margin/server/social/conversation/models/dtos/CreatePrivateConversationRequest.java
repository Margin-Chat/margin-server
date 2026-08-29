package org.margin.server.social.conversation.models.dtos;

public record CreatePrivateConversationRequest(Long recipientUserId,
                                               String encryptedContent,
                                               boolean encrypted) {
}
