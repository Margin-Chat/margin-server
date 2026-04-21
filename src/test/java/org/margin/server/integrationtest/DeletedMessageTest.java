package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.ConversationTestUtils;
import org.margin.server.integrationtest.utils.MessageTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeletedMessageTest extends MarginTestRunner {

    @Autowired
    private MessageRepository messageRepository;

    @Test
    void getMessages_doesNotReturnDeletedMessages() {
        User sender = UserTestUtils.createUser("sender", "sender@margin.chat");
        User receiver = UserTestUtils.createUser("receiver", "receiver@margin.chat");
        Conversation conversation = ConversationTestUtils.createDirectConversation(sender, receiver);

        MessageTestUtils.saveMessage(conversation, sender, "visible message", false);
        MessageTestUtils.saveMessage(conversation, sender, "deleted message", true);

        List<Message> messages = MessageTestUtils.getMessages(conversation.getId());

        assertEquals(1, messages.size());
        assertEquals("visible message", messages.getFirst().getMessage());
    }

    @Test
    void getMessages_allDeleted_returnsEmpty() {
        User sender = UserTestUtils.createUser("sender", "sender@margin.chat");
        User receiver = UserTestUtils.createUser("receiver", "receiver@margin.chat");
        Conversation conversation = ConversationTestUtils.createDirectConversation(sender, receiver);

        MessageTestUtils.saveMessage(conversation, sender, "deleted one", true);
        MessageTestUtils.saveMessage(conversation, sender, "deleted two", true);

        List<Message> messages = MessageTestUtils.getMessages(conversation.getId());

        assertTrue(messages.isEmpty());
    }
}
