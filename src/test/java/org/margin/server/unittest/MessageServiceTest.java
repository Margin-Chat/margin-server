package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.connection.ConnectionManager;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.social.channel.channel.Channel;
import org.margin.server.social.space.models.Space;
import org.margin.server.users.models.User;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
    @InjectMocks
    private MessageService messageService;

    @Test
    void sendMessage_directConversation_savesMessage() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);
        String content = "Hello!";

        Message savedMessage = createSavedMessage(100L, conversation, fromUser, content);
        List<User> members = List.of(fromUser, createUser(2L, "recipient"));

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(members);

        MessageResult result = messageService.sendMessage(fromUser, conversation, content);

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
    void sendMessage_groupConversation_returnsAllMembers() {
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

        MessageResult result = messageService.sendMessage(fromUser, conversation, content);

        assertNotNull(result);
        assertEquals(3, result.recipients().size());
        assertEquals(content, result.message().content());
        assertTrue(result.recipients().contains(fromUser));
    }

    @Test
    void sendMessage_channelConversation_returnsAllMembers() {
        User fromUser = createUser(1L, "sender");
        Channel channel = createChannel(5L);
        Conversation conversation = createConversation(10L, ConversationType.CHANNEL);
        conversation.setChannel(channel);
        String content = "Channel message";

        Message savedMessage = createSavedMessage(200L, conversation, fromUser, content);
        List<User> members = Arrays.asList(fromUser, createUser(2L, "user2"));

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(members);

        MessageResult result = messageService.sendMessage(fromUser, conversation, content);

        assertNotNull(result);
        assertEquals(2, result.recipients().size());
        assertEquals(content, result.message().content());
        verify(messageRepository).save(any(Message.class));
        verify(conversationService).getConversationMembers(conversation.getId());
    }

    @Test
    void sendDirectMessage_findsOrCreatesConversation() {
        User fromUser = createUser(1L, "sender");
        User toUser = createUser(2L, "recipient");
        String content = "Direct message content";

        Conversation dmConversation = createConversation(10L, ConversationType.DIRECT);
        Message savedMessage = createSavedMessage(100L, dmConversation, fromUser, content);

        when(conversationService.findOrCreateDirectConversation(fromUser, toUser)).thenReturn(dmConversation);
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(dmConversation.getId())).thenReturn(Arrays.asList(fromUser, toUser));

        MessageResult result = messageService.sendDirectMessage(fromUser, toUser, content);

        assertNotNull(result);
        assertEquals(content, result.message().content());
        assertEquals(2, result.recipients().size());
        assertTrue(result.recipients().contains(fromUser));
        assertTrue(result.recipients().contains(toUser));
        verify(conversationService).findOrCreateDirectConversation(fromUser, toUser);
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void sendMessage_returnsAllMembersAsRecipients() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.GROUP);
        String content = "Test message";

        Message savedMessage = createSavedMessage(100L, conversation, fromUser, content);
        User user2 = createUser(2L, "user2");
        User user3 = createUser(3L, "user3");
        List<User> allMembers = Arrays.asList(fromUser, user2, user3);

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(allMembers);

        MessageResult result = messageService.sendMessage(fromUser, conversation, content);

        assertEquals(3, result.recipients().size());
        assertTrue(result.recipients().contains(fromUser));
        assertTrue(result.recipients().contains(user2));
        assertTrue(result.recipients().contains(user3));
    }

    @Test
    void sendMessage_handlesEmptyContent() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);
        String content = "";

        Message savedMessage = createSavedMessage(100L, conversation, fromUser, content);

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId()))
                .thenReturn(Arrays.asList(fromUser, createUser(2L, "recipient")));

        MessageResult result = messageService.sendMessage(fromUser, conversation, content);

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
        conversation.setCreatedAt(LocalDateTime.now());
        return conversation;
    }

    private Channel createChannel(Long id) {
        Channel channel = new Channel();
        channel.setId(id);
        Space space = new Space();
        space.setId(1L);
        channel.setSpace(space);
        return channel;
    }

    private Message createSavedMessage(Long id, Conversation conversation, User fromUser, String content) {
        Message message = new Message();
        message.setId(id);
        message.setConversation(conversation);
        message.setFromUser(fromUser);
        message.setMessage(content);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }
}