package org.margin.server.social.conversation.models.dtos;

public record SendConversationInviteRequest(String email, Boolean encrypted) {
    public boolean isEncrypted() {
        return Boolean.TRUE.equals(encrypted);
    }
}
