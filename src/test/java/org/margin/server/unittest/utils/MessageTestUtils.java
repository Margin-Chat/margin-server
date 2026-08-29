package org.margin.server.unittest.utils;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.messages.models.Message;
import org.margin.server.users.models.User;

import java.time.Instant;

public class MessageTestUtils {

    public static Message createSavedMessage(Long id, Conversation conversation, User fromUser, String content) {
        Message message = new Message();
        message.setId(id);
        message.setConversation(conversation);
        message.setFromUserId(fromUser.getId());
        message.setMessage(content);
        message.setCreatedAt(Instant.now());
        return message;
    }
}
