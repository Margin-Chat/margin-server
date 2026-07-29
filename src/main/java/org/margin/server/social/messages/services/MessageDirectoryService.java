package org.margin.server.social.messages.services;

import org.margin.server.social.api.MessageDirectory;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.margin.server.users.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MessageDirectoryService implements MessageDirectory {

    private final MessageRepository messageRepository;
    private final ConversationMemberRepository conversationMemberRepository;

    public MessageDirectoryService(MessageRepository messageRepository,
                                   ConversationMemberRepository conversationMemberRepository) {
        this.messageRepository = messageRepository;
        this.conversationMemberRepository = conversationMemberRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MessageContext contextOf(Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        Conversation conversation = message.getConversation();
        Long marginId = conversation.getChannel() == null ? null
                : conversation.getChannel().getSpace().getMargin().getId();

        return new MessageContext(message.getFromUser().getId(), conversation.getId(), marginId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> threadFollowerIds(Long conversationId) {
        return conversationMemberRepository.findUsersByConversationId(conversationId).stream()
                .map(User::getId)
                .toList();
    }
}
