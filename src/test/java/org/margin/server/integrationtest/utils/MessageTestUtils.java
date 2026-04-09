package org.margin.server.integrationtest.utils;

import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

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

    public static List<Message> getMessages(Long conversationId, int limit) {
        return messageRepository.findRecentMessages(conversationId, Pageable.ofSize(limit));
    }
}