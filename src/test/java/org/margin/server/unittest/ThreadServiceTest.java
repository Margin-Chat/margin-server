package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.channel.ChannelLookup;
import org.margin.server.users.api.UserLookup;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.models.ChannelType;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationMember;
import org.margin.server.social.api.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.conversation.services.ConversationCreationService;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.conversation.services.ThreadService;
import org.margin.server.social.messages.events.MessageSentEvent;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.presence.PresenceService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.margin.server.unittest.utils.ChannelTestUtils.createChannel;
import static org.margin.server.unittest.utils.ConversationTestUtils.createConversation;
import static org.margin.server.unittest.utils.MessageTestUtils.createSavedMessage;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreadServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private ConversationCreationService conversationCreationService;
    @Mock
    private ConversationService conversationService;
    @Mock
    private MessageService messageService;
    @Mock
    private ChannelLookup channelLookup;
    @Mock
    private PresenceService presenceService;
    @Mock
    private UserLookup userLookup;

    @InjectMocks
    private ThreadService threadService;

    private User alice;
    private User bob;
    private Channel threadChannel;
    private Conversation channelConversation;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(userLookup.dtoOf(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(i -> new org.margin.server.users.models.dtos.UserDTO(i.getArgument(0), "u", null, null, null, false));
        alice = createUser(1L, "alice");
        bob = createUser(2L, "bob");
        threadChannel = createChannel(5L);
        threadChannel.setChannelType(ChannelType.Thread);
        channelConversation = createConversation(10L, ConversationType.CHANNEL);
    }

    private void stubThreadChannel() {
        when(channelLookup.getById(5L)).thenReturn(threadChannel);
        lenient().when(conversationService.getByChannelId(5L)).thenReturn(channelConversation);
    }

    @Test
    void createPost_inChatChannel_throwsBadRequest() {
        threadChannel.setChannelType(ChannelType.Communication);
        when(channelLookup.getById(5L)).thenReturn(threadChannel);

        assertThatThrownBy(() -> threadService.createPost(alice.getId(), 5L, "title", "body"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createPost_byNonMember_throwsForbidden() {
        stubThreadChannel();
        when(conversationService.isUserMember(10L, alice.getId())).thenReturn(false);

        assertThatThrownBy(() -> threadService.createPost(alice.getId(), 5L, "title", "body"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createPost_withBlankTitle_throwsBadRequest() {
        stubThreadChannel();
        when(conversationService.isUserMember(10L, alice.getId())).thenReturn(true);

        assertThatThrownBy(() -> threadService.createPost(alice.getId(), 5L, "  ", "body"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(conversationCreationService, never()).createThreadConversation(any(), anyString());
    }

    @Test
    void createPost_followsAuthorAndSendsBody() {
        stubThreadChannel();
        when(conversationService.isUserMember(10L, alice.getId())).thenReturn(true);
        Conversation post = createConversation(11L, ConversationType.THREAD);
        post.setName("title");
        post.setParentConversationId(10L);
        when(conversationCreationService.createThreadConversation(channelConversation, "title"))
                .thenReturn(post);
        when(conversationMemberRepository.findByConversationIdAndUserId(11L, alice.getId()))
                .thenReturn(Optional.empty());

        threadService.createPost(alice.getId(), 5L, "title", "the body");

        verify(conversationCreationService).createConversationMember(post, alice.getId());
        verify(messageService).sendMessage(alice.getId(), "the body", post.getId(), List.of());
        verify(conversationService).toThreadDTO(post, alice.getId());
    }

    @Test
    void onMessageSent_nonThreadConversation_isIgnored() {
        Message channelMessage = createSavedMessage(100L, channelConversation, alice, "hi");
        MessageDTO dto = MessageDTO.from(channelMessage).withAuthor(new org.margin.server.users.models.dtos.UserDTO(channelMessage.getFromUserId(), "u", null, null, null, false)).build();

        threadService.onMessageSent(new MessageSentEvent(dto, List.of(alice.getId(), bob.getId())));

        verify(conversationCreationService, never()).createConversationMember(any(), any());
    }

    @Test
    void onMessageSent_threadReply_autoFollowsSender() {
        Conversation post = createConversation(11L, ConversationType.THREAD);
        post.setParentConversationId(10L);
        Message reply = createSavedMessage(102L, post, bob, "reply");
        MessageDTO dto = MessageDTO.from(reply).withAuthor(new org.margin.server.users.models.dtos.UserDTO(reply.getFromUserId(), "u", null, null, null, false)).build();

        when(conversationMemberRepository.findByConversationIdAndUserId(11L, bob.getId()))
                .thenReturn(Optional.empty(), Optional.empty());
        when(conversationService.getById(11L)).thenReturn(post);

        threadService.onMessageSent(new MessageSentEvent(dto, List.of(alice.getId(), bob.getId())));

        verify(conversationCreationService).createConversationMember(post, bob.getId());
    }

    @Test
    void onMessageSent_replyByExistingFollower_doesNotDuplicateFollow() {
        Conversation post = createConversation(11L, ConversationType.THREAD);
        Message reply = createSavedMessage(102L, post, bob, "reply");
        MessageDTO dto = MessageDTO.from(reply).withAuthor(new org.margin.server.users.models.dtos.UserDTO(reply.getFromUserId(), "u", null, null, null, false)).build();

        lenient().when(conversationMemberRepository.findByConversationIdAndUserId(eq(11L), eq(bob.getId())))
                .thenReturn(Optional.of(new ConversationMember()));

        threadService.onMessageSent(new MessageSentEvent(dto, List.of(alice.getId(), bob.getId())));

        verify(conversationCreationService, never()).createConversationMember(any(), any());
    }

    @Test
    void getFollowedThreads_emptyWithoutMemberships() {
        when(conversationMemberRepository.findThreadMembershipsByUserId(alice.getId()))
                .thenReturn(List.of());

        assertThat(threadService.getFollowedThreads(alice.getId())).isEmpty();
        verify(conversationRepository, never()).findThreadSummariesForThreads(anyList(), anyLong());
    }
}
