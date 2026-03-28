package org.margin.server.social.conversation.validations;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.services.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConversationAuthorizationService {
    private final ConversationService conversationService;

    public ConversationAuthorizationService(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public void requireConversationMember(Long conversationId, Long userId) {
        if (!conversationService.isUserMember(conversationId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this conversation");
        }
    }

    public void requireRecipientNotSelf(Long userId, Long otherUserId) {
        if (otherUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot get conversation with yourself");
        }
    }

    public void requireConversationTypeGroup(Conversation conversation) {
        if (conversation.getType() != ConversationType.GROUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can only add members to group conversations");
        }
    }
}
