package org.margin.server.social.exceptions;

public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(String message) {
        super(message);
    }
    public ConversationNotFoundException(Long conversationId) {
        super("Conversation not found: " + conversationId);
    }
}