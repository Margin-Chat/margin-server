package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.models.dtos.ConversationDTO;
import org.margin.server.social.conversation.models.dtos.DirectConversationDTO;
import org.margin.server.social.conversation.models.dtos.GroupConversationDTO;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.websocket.connection.ConnectionManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        // 1. Create the real instance
        ConversationService serviceImpl = new ConversationService(
                conversationRepository,
                conversationMemberRepository,
                userRepository,
                null, // self placeholder
                connectionManager
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

        when(conversationMemberRepository.findUsersByConversationId(10L)).thenReturn(List.of(user1, user2));

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
    @DisplayName("Should create group and add both creator and invited members")
    void createGroupConversation_Success() {
        User creator = createUser(1L);
        User member = createUser(2L);

        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> {
            Conversation c = i.getArgument(0);
            c.setId(99L);
            return c;
        });
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));

        Conversation result = conversationService.createGroupConversation(creator, List.of(2L), "New Group");

        assertNotNull(result);
        verify(conversationMemberRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("addMember should use self.getById and succeed")
    void addMember_Success() {
        Conversation conv = createConversation(1L, ConversationType.GROUP);
        User user = createUser(2L);

        // Mock the self-call 'getById'
        doReturn(conv).when(conversationService).getById(1L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> conversationService.addMember(1L, 2L));
        verify(conversationMemberRepository).save(any());
    }

    @Test
    @DisplayName("getById should throw exception when not found")
    void getById_NotFound_ThrowsException() {
        when(conversationRepository.findByIdWithAssociations(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> conversationService.getById(1L));
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Conversation createConversation(Long id, ConversationType type) {
        Conversation conv = new Conversation();
        conv.setId(id);
        conv.setType(type);
        return conv;
    }
}