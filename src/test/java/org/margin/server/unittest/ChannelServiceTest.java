package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.channel.repositories.ChannelRepository;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.channel.channel.Channel;
import org.margin.server.social.channel.channel.ChannelType;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.models.User;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private SpacesService spacesService;
    @Mock private ConversationMemberRepository conversationMemberRepository;

    @InjectMocks
    private ChannelService channelService;

    private Space testSpace;

    @BeforeEach
    void setUp() {
        User testUser = new User();
        testUser.setId(1L);

        testSpace = new Space();
        testSpace.setId(100L);

        SpaceMember member = new SpaceMember();
        member.setUser(testUser);
        testSpace.setMembers(List.of(member));
    }

    @Test
    @DisplayName("Should create channel, conversation, and map all space members")
    void createChannel_Success() {
        String name = "General";
        String desc = "Main Chat";

        when(channelRepository.save(any(Channel.class))).thenAnswer(i -> i.getArgument(0));

        Channel result = channelService.createChannel(testSpace, name, desc);

        assertNotNull(result);
        assertEquals(name, result.getName());
        assertEquals(ChannelType.Communication, result.getChannelType());
        assertEquals(testSpace, result.getSpace());

        ArgumentCaptor<Conversation> convCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(convCaptor.capture());
        Conversation savedConv = convCaptor.getValue();

        assertEquals(name, savedConv.getName());
        assertEquals(ConversationType.CHANNEL, savedConv.getType());
        assertEquals(result, savedConv.getChannel());

        verify(conversationMemberRepository).saveAll(argThat(members -> {
            var list = (List<?>) members;
            return list.size() == 1;
        }));
    }

    @Test
    @DisplayName("Should delete channel and its associated conversation and members")
    void deleteChannel_Success() {
        // Arrange
        Long channelId = 1L;
        Channel channel = new Channel();
        Conversation conversation = new Conversation();
        channel.setConversation(conversation);

        when(channelRepository.findById(channelId)).thenReturn(java.util.Optional.of(channel));
        when(conversationMemberRepository.findByConversation(conversation)).thenReturn(List.of());

        // Act
        channelService.deleteChannel(channelId);

        // Assert
        verify(conversationMemberRepository).deleteAll(any());
        verify(conversationRepository).delete(conversation);
        verify(channelRepository).delete(channel);
    }
}