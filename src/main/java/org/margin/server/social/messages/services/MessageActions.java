package org.margin.server.social.messages.services;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.MessageReaction;
import org.margin.server.social.messages.repositories.MessageReactionRepository;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MessageActions {

    private final MessageRepository messageRepository;
    private final MessageReactionRepository messageReactionRepository;

    public MessageActions(MessageRepository messageRepository, MessageReactionRepository messageReactionRepository) {
        this.messageRepository = messageRepository;
        this.messageReactionRepository = messageReactionRepository;
    }

    @Transactional
    public Message createMessage(User fromUser, Conversation conversation, String content) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setFromUser(fromUser);
        message.setMessage(content);
        message.setCreatedAt(Instant.now());

        return messageRepository.save(message);
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
        return messageReactionRepository.save(new MessageReaction(message, user, emoji));
    }
}
