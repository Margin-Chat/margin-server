package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.social.space.models.Space;
import org.margin.server.users.models.User;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ConversationService conversationService;
    @Mock
    private ConnectionManager connectionManager;
    @Mock
    private WebSocketDeliveryService webSocketDeliveryService;
    @InjectMocks
    private MessageService messageService;

    @Test
    void createMessage_directConversation_savesMessage() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);
        String content = "Hello!";

        Message savedMessage = createSavedMessage(100L, conversation, fromUser, content);
        List<User> members = List.of(fromUser, createUser(2L, "recipient"));

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(members);

        MessageResult result = messageService.createMessage(fromUser, conversation, content);

        assertNotNull(result);
        assertEquals(content, result.message().content());
        assertEquals(2, result.recipients().size());
        assertTrue(result.recipients().contains(fromUser));

        verify(messageRepository).save(argThat(message ->
                message.getMessage().equals(content) &&
                        message.getConversation().equals(conversation) &&
                        message.getFromUser().equals(fromUser)
        ));
        verify(conversationService).getConversationMembers(conversation.getId());
    }

    @Test
    void createMessage_groupConversation_returnsAllMembers() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.GROUP);
        String content = "Hello group!";

        Message savedMessage = createSavedMessage(100L, conversation, fromUser, content);
        List<User> allMembers = Arrays.asList(
                fromUser,
                createUser(2L, "user2"),
                createUser(3L, "user3")
        );

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(allMembers);

        MessageResult result = messageService.createMessage(fromUser, conversation, content);

        assertNotNull(result);
        assertEquals(3, result.recipients().size());
        assertEquals(content, result.message().content());
        assertTrue(result.recipients().contains(fromUser));
    }

    @Test
    void createMessage_channelConversation_returnsAllMembers() {
        User fromUser = createUser(1L, "sender");
        Channel channel = createChannel(5L);
        Conversation conversation = createConversation(10L, ConversationType.CHANNEL);
        conversation.setChannel(channel);
        String content = "Channel message";

        Message savedMessage = createSavedMessage(200L, conversation, fromUser, content);
        List<User> members = Arrays.asList(fromUser, createUser(2L, "user2"));

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(members);

        MessageResult result = messageService.createMessage(fromUser, conversation, content);

        assertNotNull(result);
        assertEquals(2, result.recipients().size());
        assertEquals(content, result.message().content());
        verify(messageRepository).save(any(Message.class));
        verify(conversationService).getConversationMembers(conversation.getId());
    }

    @Test
    void sendMessage_createsAndDeliversMessage() {
        User fromUser = createUser(1L, "sender");
        User toUser = createUser(2L, "recipient");
        String content = "Direct message content";

        Conversation dmConversation = createConversation(10L, ConversationType.DIRECT);
        Message savedMessage = createSavedMessage(100L, dmConversation, fromUser, content);

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(dmConversation.getId()))
                .thenReturn(Arrays.asList(fromUser, toUser));

        messageService.sendMessage(fromUser, content, dmConversation);

        verify(messageRepository).save(any(Message.class));
        verify(webSocketDeliveryService).notifyMessage(
                argThat(msg -> msg.content().equals(content)),
                argThat(recipients -> recipients.contains(toUser)),
                eq(ConversationType.DIRECT)
        );
    }

    @Test
    void createMessage_returnsAllMembersAsRecipients() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.GROUP);
        String content = "Test message";

        Message savedMessage = createSavedMessage(100L, conversation, fromUser, content);
        User user2 = createUser(2L, "user2");
        User user3 = createUser(3L, "user3");
        List<User> allMembers = Arrays.asList(fromUser, user2, user3);

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(allMembers);

        MessageResult result = messageService.createMessage(fromUser, conversation, content);

        assertEquals(3, result.recipients().size());
        assertTrue(result.recipients().contains(fromUser));
        assertTrue(result.recipients().contains(user2));
        assertTrue(result.recipients().contains(user3));
    }

    @Test
    void createMessage_handlesEmptyContent() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);
        String content = "";

        Message savedMessage = createSavedMessage(100L, conversation, fromUser, content);

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId()))
                .thenReturn(Arrays.asList(fromUser, createUser(2L, "recipient")));

        MessageResult result = messageService.createMessage(fromUser, conversation, content);

        assertNotNull(result);
        assertEquals("", result.message().content());
    }

    private User createUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private Conversation createConversation(Long id, ConversationType type) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setType(type);
        conversation.setCreatedAt(Instant.now());
        return conversation;
    }

    private Channel createChannel(Long id) {
        Channel channel = new Channel();
        channel.setId(id);
        Space space = new Space();
        space.setId(1L);
        Margin margin = new Margin();
        margin.setId(1L);
        space.setMargin(margin);
        channel.setSpace(space);
        return channel;
    }

    private Message createSavedMessage(Long id, Conversation conversation, User fromUser, String content) {
        Message message = new Message();
        message.setId(id);
        message.setConversation(conversation);
        message.setFromUser(fromUser);
        message.setMessage(content);
        message.setCreatedAt(Instant.now());
        return message;
    }
}