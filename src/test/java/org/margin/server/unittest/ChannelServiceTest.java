package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.models.ChannelType;
import org.margin.server.social.channel.repositories.ChannelRepository;
import org.margin.server.social.channel.services.ChannelCreationService;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.api.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.users.models.User;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.margin.server.unittest.utils.SpaceTestUtils.createSpace;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository conversationMemberRepository;
    @Mock
    private ChannelCreationService channelCreationService;
    @Mock
    private ConversationService conversationService;

    @InjectMocks
    private ChannelService channelService;

    private Space testSpace;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = createUser(1L);

        testSpace = createSpace(100L);

        SpaceMember member = new SpaceMember();
        member.setUserId(testUser.getId());
        testSpace.setMembers(List.of(member));
    }

    @Test
    @DisplayName("Should create channel, conversation, and map all space members")
    void createChannel_Success() {
        String name = "General";
        String desc = "Main Chat";

        Channel channel = new Channel();
        channel.setName(name);
        channel.setChannelType(ChannelType.Communication);
        channel.setSpace(testSpace);

        Conversation conversation = new Conversation();
        conversation.setId(1L);
        conversation.setName(name);
        conversation.setType(ConversationType.CHANNEL);

        when(channelCreationService.createChannel(testSpace, name, desc)).thenReturn(channel);
        when(conversationService.createNewConversationForUsers(
                eq(ConversationType.CHANNEL), eq(channel), anyList())).thenReturn(conversation);
        when(channelRepository.save(any(Channel.class))).thenAnswer(i -> i.getArgument(0));

        Channel result = channelService.createNewChannel(testSpace, name, desc);

        assertNotNull(result);
        assertEquals(name, result.getName());
        assertEquals(conversation, result.getConversation());
        assertEquals(testSpace, result.getSpace());

        verify(channelCreationService).createChannel(testSpace, name, desc);
        verify(conversationService).createNewConversationForUsers(
                eq(ConversationType.CHANNEL), eq(channel), argThat(ids -> ids.size() == 1 && ids.contains(testUser.getId())));
        verify(channelRepository).save(channel);
    }

    @Test
    @DisplayName("Should delete channel and its associated conversation and members")
    void deleteChannel_Success() {
        Long channelId = 1L;
        Channel channel = new Channel();
        Conversation conversation = new Conversation();
        channel.setConversation(conversation);

        when(channelRepository.findById(channelId)).thenReturn(java.util.Optional.of(channel));
        when(conversationMemberRepository.findByConversation(conversation)).thenReturn(List.of());

        channelService.deleteChannel(channelId);

        verify(conversationMemberRepository).deleteAll(any());
        verify(channelRepository).save(channel);
        assertNotNull(channel.getDeletedAt());
        assertNotNull(conversation.getDeletedAt());
    }
}