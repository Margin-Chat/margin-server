package org.margin.server.unittest.utils;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.api.ConversationType;

import java.time.Instant;

public class ConversationTestUtils {

    public static Conversation createConversation(Long id, ConversationType type) {
        Conversation conv = new Conversation();
        conv.setId(id);
        conv.setType(type);
        conv.setCreatedAt(Instant.now());
        return conv;
    }
}
