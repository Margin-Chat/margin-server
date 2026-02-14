package org.margin.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.conversation.Conversation;
import org.margin.server.social.conversation.ConversationType;
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

    @InjectMocks
    private MessageService messageService;

    @Test
    void sendMessage_directConversation_savesMessage() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);
        String content = "Hello!";

        Message savedMessage = new Message();
        savedMessage.setId(100L);
        savedMessage.setConversation(conversation);
        savedMessage.setFromUser(fromUser);
        savedMessage.setMessage(content);
        savedMessage.setCreatedAt(LocalDateTime.now());

        List<User> recipients = List.of(createUser(2L, "recipient"));

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(
                Arrays.asList(fromUser, recipients.get(0))
        );

        MessageResult result = messageService.sendMessage(fromUser, conversation, content);

        assertNotNull(result);
        assertNotNull(result.message());
        assertEquals(1, result.recipients().size());
        assertEquals(content, result.message().content());

        verify(messageRepository).save(argThat(message ->
                message.getMessage().equals(content) &&
                        message.getConversation().equals(conversation) &&
                        message.getFromUser().equals(fromUser)
        ));
        verify(conversationService).getConversationMembers(conversation.getId());
    }

    @Test
    void sendMessage_groupConversation_savesMessage() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.GROUP);
        String content = "Hello group!";

        Message savedMessage = new Message();
        savedMessage.setId(100L);
        savedMessage.setConversation(conversation);
        savedMessage.setFromUser(fromUser);
        savedMessage.setMessage(content);
        savedMessage.setCreatedAt(LocalDateTime.now());

        List<User> allMembers = Arrays.asList(
                fromUser,
                createUser(2L, "user2"),
                createUser(3L, "user3")
        );

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(allMembers);

        MessageResult result = messageService.sendMessage(fromUser, conversation, content);

        assertNotNull(result);
        assertEquals(2, result.recipients().size()); // Excludes sender
        assertEquals(content, result.message().content());

        verify(messageRepository).save(argThat(message ->
                message.getMessage().equals(content)
        ));
    }

    @Test
    void sendMessage_channelConversation_savesMessage() {
        User fromUser = createUser(1L, "sender");
        Channel channel = createChannel(5L);
        Conversation conversation = createConversation(10L, ConversationType.CHANNEL);
        conversation.setChannel(channel);
        String content = "Channel message";

        Message savedMessage = new Message();
        savedMessage.setId(200L);
        savedMessage.setConversation(conversation);
        savedMessage.setFromUser(fromUser);
        savedMessage.setMessage(content);
        savedMessage.setCreatedAt(LocalDateTime.now());

        List<User> recipients = Arrays.asList(
                fromUser,
                createUser(2L, "user2")
        );

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(recipients);

        MessageResult result = messageService.sendMessage(fromUser, conversation, content);

        assertNotNull(result);
        assertNotNull(result.message());
        assertEquals(1, result.recipients().size());
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

        Message savedMessage = new Message();
        savedMessage.setId(100L);
        savedMessage.setConversation(dmConversation);
        savedMessage.setFromUser(fromUser);
        savedMessage.setMessage(content);
        savedMessage.setCreatedAt(LocalDateTime.now());

        when(conversationService.findOrCreateDirectConversation(fromUser, toUser))
                .thenReturn(dmConversation);
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(dmConversation.getId()))
                .thenReturn(Arrays.asList(fromUser, toUser));

        MessageResult result = messageService.sendDirectMessage(fromUser, toUser, content);

        assertNotNull(result);
        assertEquals(content, result.message().content());
        assertEquals(1, result.recipients().size());
        verify(conversationService).findOrCreateDirectConversation(fromUser, toUser);
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void sendMessage_excludesSenderFromRecipients() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.GROUP);
        String content = "Test message";

        Message savedMessage = new Message();
        savedMessage.setId(100L);
        savedMessage.setConversation(conversation);
        savedMessage.setFromUser(fromUser);
        savedMessage.setMessage(content);
        savedMessage.setCreatedAt(LocalDateTime.now());

        User user2 = createUser(2L, "user2");
        User user3 = createUser(3L, "user3");
        List<User> allMembers = Arrays.asList(fromUser, user2, user3);

        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(allMembers);

        MessageResult result = messageService.sendMessage(fromUser, conversation, content);

        assertEquals(2, result.recipients().size());
        assertFalse(result.recipients().contains(fromUser));
        assertTrue(result.recipients().contains(user2));
        assertTrue(result.recipients().contains(user3));
    }

    @Test
    void sendMessage_handlesEmptyContent() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);
        String content = "";

        Message savedMessage = new Message();
        savedMessage.setId(100L);
        savedMessage.setConversation(conversation);
        savedMessage.setFromUser(fromUser);
        savedMessage.setMessage(content);
        savedMessage.setCreatedAt(LocalDateTime.now());

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
}