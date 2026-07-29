package org.margin.server.social.messages.services;

import org.margin.server.social.channel.models.ChannelType;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationInviteStatus;
import org.margin.server.social.api.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.messages.repositories.MessageReactionRepository;
import org.margin.server.users.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MessageValidationService {
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageReactionRepository messageReactionRepository;

    public MessageValidationService(ConversationMemberRepository conversationMemberRepository, MessageReactionRepository messageReactionRepository) {
        this.conversationMemberRepository = conversationMemberRepository;
        this.messageReactionRepository = messageReactionRepository;
    }

    public void validateConversationIsNotPending(User fromUser, Conversation conversation) {
        if (conversation.getType() == ConversationType.DIRECT) {
            boolean anyPending = conversationMemberRepository.findByConversation(conversation).stream()
                    .anyMatch(m -> !m.getUserId().equals(fromUser.getId())
                            && m.getInviteStatus() == ConversationInviteStatus.PENDING);
            if (anyPending) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Cannot send messages until the invite is accepted");
            }
        }
    }

    public void validateNotThreadChannelConversation(Conversation conversation) {
        if (conversation.getType() == ConversationType.CHANNEL
                && conversation.getChannel() != null
                && conversation.getChannel().getChannelType() == ChannelType.Thread) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thread channels only accept posts; reply inside a post instead");
        }
    }

    public void validateDuplicateEmojiForMessage(User user, Long messageId, String emoji) {
        if (messageReactionRepository.existsByMessageIdAndUserIdAndEmoji(messageId, user.getId(), emoji)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reaction already exists");
        }
    }
}
