package org.margin.server.integrationtest.utils;

import org.margin.server.social.messages.models.MessageReaction;
import org.margin.server.social.messages.repositories.MessageReactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MessageReactionTestUtils {

    private static MessageReactionRepository messageReactionRepository;

    @Autowired
    public MessageReactionTestUtils(MessageReactionRepository messageReactionRepository) {
        MessageReactionTestUtils.messageReactionRepository = messageReactionRepository;
    }

    public static List<MessageReaction> getReactionsForMessage(Long messageId) {
        return messageReactionRepository.findByMessageIdIn(List.of(messageId));
    }
}
