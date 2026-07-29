package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.conversation.services.ConversationValidationService;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.MessageReaction;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.messages.models.dtos.MessageReactionDTO;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.repositories.MessageReactionRepository;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.margin.server.social.messages.services.MessageActions;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.social.messages.services.MessageValidationService;
import org.margin.server.social.api.MessageAttachments;
import org.margin.server.users.models.User;
import org.margin.server.presence.PresenceService;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.margin.server.unittest.utils.ChannelTestUtils.createChannelWithSpaceAndMargin;
import static org.margin.server.unittest.utils.ConversationTestUtils.createConversation;
import static org.margin.server.unittest.utils.MessageTestUtils.createSavedMessage;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private MessageReactionRepository messageReactionRepository;
    @Mock
    private MessageAttachments messageAttachments;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private ConversationService conversationService;
    @Mock
    private PresenceService presenceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MessageActions messageActions;
    @Mock
    private MessageValidationService messageValidationService;
    @Mock
    private ConversationValidationService conversationValidationService;
    @InjectMocks
    private MessageService messageService;

    private static final List<Long> attachmentIds = List.of();

    @Test
    void createMessage_directConversation_savesMessageForUsers() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);
        String content = "Hello!";

        Message savedMessage = createSavedMessage(100L, conversation, fromUser, content);
        List<User> members = List.of(fromUser, createUser(2L, "recipient"));

        when(messageActions.createMessage(fromUser, conversation, content, attachmentIds)).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(members);

        MessageResult result = messageService.createMessageForUsers(fromUser, conversation, content, attachmentIds);

        assertNotNull(result);
        assertEquals(content, result.message().content());
        assertEquals(2, result.recipients().size());
        assertTrue(result.recipients().contains(fromUser));

        verify(messageActions).createMessage(fromUser, conversation, content, attachmentIds);
        verify(conversationService).getConversationMembers(conversation.getId());
    }

    @Test
    void createMessage_ForUsers_groupConversation_returnsAllMembers() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.GROUP);
        String content = "Hello group!";

        Message savedMessage = createSavedMessage(100L, conversation, fromUser, content);
        List<User> allMembers = Arrays.asList(
                fromUser,
                createUser(2L, "user2"),
                createUser(3L, "user3")
        );

        when(messageActions.createMessage(fromUser, conversation, content, attachmentIds)).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(allMembers);

        MessageResult result = messageService.createMessageForUsers(fromUser, conversation, content, attachmentIds);

        assertNotNull(result);
        assertEquals(3, result.recipients().size());
        assertEquals(content, result.message().content());
        assertTrue(result.recipients().contains(fromUser));
    }

    @Test
    void createMessage_ForUsers_channelConversation_returnsAllMembers() {
        User fromUser = createUser(1L, "sender");
        Channel channel = createChannelWithSpaceAndMargin(5L, 1L, 1L);
        Conversation conversation = createConversation(10L, ConversationType.CHANNEL);
        conversation.setChannel(channel);
        String content = "Channel message";

        Message savedMessage = createSavedMessage(200L, conversation, fromUser, content);
        List<User> members = Arrays.asList(fromUser, createUser(2L, "user2"));

        when(messageActions.createMessage(fromUser, conversation, content, attachmentIds)).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(members);

        MessageResult result = messageService.createMessageForUsers(fromUser, conversation, content, attachmentIds);

        assertNotNull(result);
        assertEquals(2, result.recipients().size());
        assertEquals(content, result.message().content());
        verify(messageActions).createMessage(fromUser, conversation, content, attachmentIds);
        verify(conversationService).getConversationMembers(conversation.getId());
    }

    @Test
    void sendMessage_createsAndDeliversMessage() {
        User fromUser = createUser(1L, "sender");
        User toUser = createUser(2L, "recipient");
        String content = "Direct message content";

        Conversation dmConversation = createConversation(10L, ConversationType.DIRECT);
        Message savedMessage = createSavedMessage(100L, dmConversation, fromUser, content);

        when(messageActions.createMessage(fromUser, dmConversation, content, attachmentIds)).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(dmConversation.getId()))
                .thenReturn(Arrays.asList(fromUser, toUser));

        messageService.sendMessage(fromUser, content, dmConversation, attachmentIds);

        verify(messageActions).createMessage(fromUser, dmConversation, content, attachmentIds);
        verify(eventPublisher).publishEvent(argThat(e ->
                e instanceof org.margin.server.social.messages.events.MessageSentEvent evt
                && evt.getMessage().content().equals(content)
                && evt.getRecipients().contains(toUser)));
    }

    @Test
    void createMessage_ForUsers_returnsAllMembersAsRecipients() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.GROUP);
        String content = "Test message";

        Message savedMessage = createSavedMessage(100L, conversation, fromUser, content);
        User user2 = createUser(2L, "user2");
        User user3 = createUser(3L, "user3");
        List<User> allMembers = Arrays.asList(fromUser, user2, user3);

        when(messageActions.createMessage(fromUser, conversation, content, attachmentIds)).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(allMembers);

        MessageResult result = messageService.createMessageForUsers(fromUser, conversation, content, attachmentIds);

        assertEquals(3, result.recipients().size());
        assertTrue(result.recipients().contains(fromUser));
        assertTrue(result.recipients().contains(user2));
        assertTrue(result.recipients().contains(user3));
    }

    @Test
    void createMessage_ForUsers_handlesEmptyContent() {
        User fromUser = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);
        String content = "";

        Message savedMessage = createSavedMessage(100L, conversation, fromUser, content);

        when(messageActions.createMessage(fromUser, conversation, content, attachmentIds)).thenReturn(savedMessage);
        when(conversationService.getConversationMembers(conversation.getId()))
                .thenReturn(Arrays.asList(fromUser, createUser(2L, "recipient")));

        MessageResult result = messageService.createMessageForUsers(fromUser, conversation, content, attachmentIds);

        assertNotNull(result);
        assertEquals("", result.message().content());
    }

    @Test
    void getConversationMessages_doesNotReturnDeletedMessages() {
        User user = createUser(1L, "sender");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);

        Message visible = createSavedMessage(1L, conversation, user, "visible");
        Message deleted = createSavedMessage(2L, conversation, user, "deleted");
        deleted.setIsDeleted(true);

        when(messageRepository.findRecentMessages(eq(10L), any())).thenReturn(List.of(visible));

        List<MessageDTO> result = messageService.getConversationMessages(conversation, 50, null);

        assertEquals(1, result.size());
        assertEquals("visible", result.get(0).content());
        verify(messageRepository).findRecentMessages(eq(10L), any());
    }

    @Test
    void addReaction_savesAndReturnsDTO() {
        User user = createUser(1L, "reactor");
        user.setDisplayName("Reactor");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);
        Message message = createSavedMessage(100L, conversation, user, "Hello");

        MessageReaction saved = new MessageReaction(message, user, "👍");
        saved.setId(1L);

        when(conversationService.getById(conversation.getId())).thenReturn(conversation);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(List.of(user));
        when(messageRepository.findById(100L)).thenReturn(Optional.of(message));
        when(messageActions.createMessageReaction(any(), any(), any())).thenReturn(saved);

        MessageReactionDTO dto = messageService.addReaction(user, conversation.getId(), 100L, "👍");

        assertNotNull(dto);
        assertEquals("👍", dto.emoji());
        assertEquals(1L, dto.userId());
        assertEquals(100L, dto.messageId());
        assertEquals(10L, dto.conversationId());
        verify(messageActions).createMessageReaction(any(), any(), any());
    }

    @Test
    void addReaction_duplicate_throwsConflict() {
        User user = createUser(1L, "reactor");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);

        when(conversationService.getById(conversation.getId())).thenReturn(conversation);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Reaction already exists"))
                .when(messageValidationService).validateDuplicateEmojiForMessage(user, 100L, "👍");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.addReaction(user, conversation.getId(), 100L, "👍"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(messageReactionRepository, never()).save(any());
    }

    @Test
    void removeReaction_deletesAndReturnsDTO() {
        User user = createUser(1L, "reactor");
        user.setDisplayName("Reactor");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);
        Message message = createSavedMessage(100L, conversation, user, "Hello");

        MessageReaction reaction = new MessageReaction(message, user, "👍");
        reaction.setId(1L);

        when(conversationService.getById(conversation.getId())).thenReturn(conversation);
        when(conversationService.getConversationMembers(conversation.getId())).thenReturn(List.of(user));
        when(messageReactionRepository.findByMessageIdAndUserIdAndEmoji(100L, 1L, "👍"))
                .thenReturn(Optional.of(reaction));

        MessageReactionDTO dto = messageService.removeReaction(user, 100L, "👍", conversation.getId());

        assertNotNull(dto);
        assertEquals("👍", dto.emoji());
        assertEquals(100L, dto.messageId());
        verify(messageReactionRepository).delete(reaction);
    }

    @Test
    void removeReaction_notFound_throwsNotFound() {
        User user = createUser(1L, "reactor");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);

        when(conversationService.getById(conversation.getId())).thenReturn(conversation);
        when(messageReactionRepository.findByMessageIdAndUserIdAndEmoji(100L, 1L, "👍"))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.removeReaction(user, 100L, "👍", conversation.getId()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(messageReactionRepository, never()).delete(any());
    }
}