package org.margin.server.social.conversation.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.conversation.exceptions.ConversationValidationException;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ConversationValidationService {
    private final ConversationService conversationService;

    public ConversationValidationService(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public void validateUserIsInConversation(User user, Conversation conversation) {
        if (!conversationService.getConversationMembers(conversation.getId()).contains(user)) {
            throw new ConversationValidationException("User is not a part of the conversation");
        }
    }
}
