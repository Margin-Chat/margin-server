package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.conversation.events.TypingIndicatorEvent;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationMember;
import org.margin.server.social.conversation.models.ConversationInviteStatus;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.models.dtos.ConversationDTO;
import org.margin.server.social.conversation.models.dtos.DirectConversationDTO;
import org.margin.server.social.conversation.models.dtos.GroupConversationDTO;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.conversation.services.ConversationCreationService;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.margin.server.unittest.utils.UserTestUtils.*;
import static org.margin.server.unittest.utils.ConversationTestUtils.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ConnectionManager connectionManager;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ConversationCreationService conversationCreationService;
    @Mock
    private UserService userService;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        // 1. Create the real instance
        ConversationService serviceImpl = new ConversationService(
                conversationCreationService,
                conversationRepository,
                conversationMemberRepository,
                userRepository,
                null, // self placeholder
                connectionManager,
                eventPublisher,
                userService
        );

        // 2. Wrap it in a spy so we can mock self-calls
        conversationService = spy(serviceImpl);

        // 3. Use Reflection to point the 'self' field to the spy
        ReflectionTestUtils.setField(conversationService, "self", conversationService);
    }

    @Test
    @DisplayName("Should map DIRECT conversation correctly")
    void getConversationDTO_Direct() {
        User user1 = createUser(1L);
        User user2 = createUser(2L);
        Conversation conv = createConversation(10L, ConversationType.DIRECT);

        ConversationMember member1 = new ConversationMember();
        member1.setUser(user1);
        member1.setLastReadAt(Instant.now());

        ConversationMember member2 = new ConversationMember();
        member2.setUser(user2);
        member2.setLastReadAt(Instant.now());

        conv.setMembers(List.of(member1, member2));

        ConversationDTO result = conversationService.getConversationDTO(conv, 1L);

        assertInstanceOf(DirectConversationDTO.class, result);
        assertEquals(2L, ((DirectConversationDTO) result).otherUserId());
    }

    @Test
    @DisplayName("Should map GROUP conversation correctly")
    void getConversationDTO_Group() {
        Conversation conv = createConversation(20L, ConversationType.GROUP);
        conv.setName("Devs");

        when(conversationMemberRepository.findUsersByConversationId(20L))
                .thenReturn(List.of(createUser(1L), createUser(2L)));

        ConversationDTO result = conversationService.getConversationDTO(conv, 1L);

        assertInstanceOf(GroupConversationDTO.class, result);
        assertEquals("Devs", ((GroupConversationDTO) result).name());
        assertEquals(2, ((GroupConversationDTO) result).memberIds().size());
    }

    @Test
    @DisplayName("Should create group, add creator as member and invite others")
    void createGroupConversation_Success() {
        User creator = createUser(1L);
        creator.setEmail("creator@example.com");
        User invitee = createUser(2L);
        invitee.setEmail("invitee@example.com");

        Conversation conversation = createConversation(99L, ConversationType.GROUP);
        conversation.setName("New Group");

        when(conversationCreationService.createGroupConversation("New Group", false)).thenReturn(conversation);
        when(userRepository.findByEmail("invitee@example.com")).thenReturn(Optional.of(invitee));

        Conversation result = conversationService.createGroupConversation(
                creator, List.of("invitee@example.com"), "New Group", false);

        assertNotNull(result);
        assertEquals(99L, result.getId());
        verify(conversationCreationService).createGroupConversation("New Group", false);
        // Creator added as ACCEPTED member
        verify(conversationCreationService).createConversationMember(conversation, creator);
    }

    @Test
    @DisplayName("addMember should invite with PENDING status and fire invite event")
    void addMember_Success() {
        Conversation conv = createConversation(1L, ConversationType.GROUP);
        User adder = createUser(1L);
        User invitee = createUser(2L);

        doReturn(conv).when(conversationService).getById(1L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(invitee));

        assertDoesNotThrow(() -> conversationService.addMember(1L, 2L, adder));
        verify(conversationCreationService).createConversationMemberWithStatus(
                conv, invitee, ConversationInviteStatus.PENDING);
    }

    @Test
    @DisplayName("getById should throw exception when not found")
    void getById_NotFound_ThrowsException() {
        when(conversationRepository.findByIdWithAssociations(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> conversationService.getById(1L));
    }

    @Test
    @DisplayName("notifyTyping should publish a TypingIndicatorEvent excluding the sender")
    void notifyTyping_publishesEventExcludingSender() {
        User sender = createUser(1L, "sender");
        User other = createUser(2L, "other");
        Conversation conversation = createConversation(10L, ConversationType.GROUP);

        when(conversationMemberRepository.findUsersByConversationId(10L))
                .thenReturn(List.of(sender, other));

        conversationService.notifyTyping(sender, conversation, true);

        org.mockito.ArgumentCaptor<TypingIndicatorEvent> captor =
                org.mockito.ArgumentCaptor.forClass(TypingIndicatorEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        TypingIndicatorEvent event = captor.getValue();
        assertEquals(10L, event.getConversationId());
        assertEquals(sender, event.getUser());
        assertTrue(event.isTyping());
        assertEquals(List.of(other), event.getRecipients());
    }

    @Test
    @DisplayName("notifyTyping should forward isTyping:false unchanged")
    void notifyTyping_forwardsIsTypingFalse() {
        User sender = createUser(1L, "sender");
        User other = createUser(2L, "other");
        Conversation conversation = createConversation(10L, ConversationType.DIRECT);

        when(conversationMemberRepository.findUsersByConversationId(10L))
                .thenReturn(List.of(sender, other));

        conversationService.notifyTyping(sender, conversation, false);

        org.mockito.ArgumentCaptor<TypingIndicatorEvent> captor =
                org.mockito.ArgumentCaptor.forClass(TypingIndicatorEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertFalse(captor.getValue().isTyping());
    }

}