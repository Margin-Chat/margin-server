package org.margin.server.social.messages.services;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.margin.events.MessageAttachmentsCreatedEvent;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.MessageReaction;
import org.margin.server.social.messages.repositories.MessageReactionRepository;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class MessageActions {

    private final MessageRepository messageRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MessageActions(MessageRepository messageRepository,
                          MessageReactionRepository messageReactionRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.messageRepository = messageRepository;
        this.messageReactionRepository = messageReactionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Message createMessage(User fromUser, Conversation conversation, String content, List<Long> attachmentIds) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setFromUserId(fromUser.getId());
        message.setMessage(content);
        message.setCreatedAt(Instant.now());
        Message saved = messageRepository.save(message);

        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            attachFiles(saved, fromUser, conversation, attachmentIds);
        }
        return saved;
    }

    private void attachFiles(Message message, User sender, Conversation conversation, List<Long> attachmentIds) {
        Long channelId = conversation.getChannel() == null ? null : conversation.getChannel().getId();
        eventPublisher.publishEvent(new MessageAttachmentsCreatedEvent(message.getId(), sender.getId(), channelId, attachmentIds));
    }

    @Transactional
    public Message editMessage(Message message, String content) {
        message.setMessage(content);
        message.setIsEdited(true);
        return messageRepository.save(message);
    }

    @Transactional
    public void deleteMessage(Message message) {
        message.setIsDeleted(true);
        messageRepository.delete(message);
    }

    @Transactional
    public MessageReaction createMessageReaction(User user, Message message, String emoji) {
        return messageReactionRepository.save(new MessageReaction(message, user.getId(), emoji));
    }
}
