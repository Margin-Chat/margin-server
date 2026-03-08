package org.margin.server.social.conversation.exceptions;

public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(String message) {
        super(message);
    }
    public ConversationNotFoundException(Long conversationId) {
        super("Conversation not found: " + conversationId);
    }
}