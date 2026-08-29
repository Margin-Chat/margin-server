package org.margin.server.integrationtest.utils;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class MessageTestUtils {

    private static MessageRepository messageRepository;

    @Autowired
    public MessageTestUtils(MessageRepository messageRepository) {
        MessageTestUtils.messageRepository = messageRepository;
    }

    public static List<Message> getMessages(Long conversationId) {
        return messageRepository.findRecentMessages(conversationId, Pageable.ofSize(50));
    }

    public static void saveMessage(Conversation conversation, User fromUser, String content, boolean isDeleted) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setFromUserId(fromUser.getId());
        message.setMessage(content);
        message.setIsDeleted(isDeleted);
        message.setCreatedAt(Instant.now());
        messageRepository.save(message);
    }
}