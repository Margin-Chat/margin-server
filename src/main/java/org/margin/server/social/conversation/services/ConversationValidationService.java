package org.margin.server.social.conversation.services;

import org.springframework.modulith.NamedInterface;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.conversation.exceptions.ConversationValidationException;
import org.margin.server.social.conversation.models.Conversation;
import org.springframework.stereotype.Service;

import java.util.List;

@NamedInterface("api")
@Service
@Slf4j
public class ConversationValidationService {
    private final ConversationService conversationService;

    public ConversationValidationService(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public void validateUserIsInConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationService.getById(conversationId);
        List<Long> list = conversationService.getConversationMembers(conversation.getId());

        if (!list.contains(userId)) {
            throw new ConversationValidationException("User is not a part of the conversation");
        }
    }
}
