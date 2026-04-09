package org.margin.server.social.channel.services;

import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.exceptions.ChannelNotFoundException;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.channel.models.ChannelType;
import org.margin.server.social.channel.repositories.ChannelRepository;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.space.exceptions.SpaceNotFoundException;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.repositories.SpacesRepository;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ChannelService {
    private final ChannelCreationService channelCreationService;
    private final ConversationService conversationService;
    private final ChannelRepository channelRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MarginMapper marginMapper;
    private final SpacesRepository spacesRepository;

    public ChannelService(ChannelCreationService channelCreationService,
                          ConversationService conversationService,
                          ChannelRepository channelRepository,
                          ConversationRepository conversationRepository,
                          ConversationMemberRepository conversationMemberRepository,
                          MarginMapper marginMapper, SpacesRepository spacesRepository) {
        this.channelCreationService = channelCreationService;
        this.conversationService = conversationService;
        this.channelRepository = channelRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.marginMapper = marginMapper;
        this.spacesRepository = spacesRepository;
    }

    @Transactional(readOnly = true)
    public List<ChannelDTO> getChannelsForSpaceAsDto(Long spaceId) {
        return channelRepository.getChannelsBySpaceId(spaceId).stream()
                .map(marginMapper::channelToDto)
                .toList();
    }

    @Transactional
    public ChannelDTO createChannelAsDto(Long spaceId, String name, String description) {
        Space space = spacesRepository.findById(spaceId).orElseThrow(() -> new SpaceNotFoundException(spaceId));
        Channel channel = channelCreationService.createChannel(space, name, description);
        List<User> users = space.getMembers().stream()
                .map(SpaceMember::getUser)
                .toList();
        Conversation conversation =
                conversationService.createNewConversationForUsers(ConversationType.CHANNEL, channel, users);
        channel.setConversation(conversation);
        channelRepository.save(channel);
        return marginMapper.channelToDto(channel);
    }

    @Transactional
    public ChannelDTO updateChannelAsDto(ChannelDTO channelDTO) {
        Channel channel = getById(channelDTO.id());
        channel.setName(channelDTO.name());
        channel.setDescription(channelDTO.description());
        channel.setChannelType(ChannelType.Communication);
        return marginMapper.channelToDto(channelRepository.save(channel));
    }

    @Transactional
    public void deleteChannel(Long channelId) {
        Channel channel = getById(channelId);
        Conversation conversation = channel.getConversation();
        conversationMemberRepository.deleteAll(conversationMemberRepository.findByConversation(conversation));
        conversationRepository.delete(conversation);
        channelRepository.delete(channel);
    }

    public Channel getById(Long id) {
        return channelRepository.findById(id).orElseThrow(() -> new ChannelNotFoundException(id));
    }

    @Transactional
    public void removeUserFromChannels(Long userId, List<Channel> channels) {
        for (Channel channel : channels) {
            conversationService.removeMember(channel.getConversation().getId(), userId);
        }
    }

    public List<Channel> getChannelsForSpace(Long spaceId) {
        return channelRepository.getChannelsBySpaceId(spaceId);
    }

    public Channel createNewChannel(Space space, String name, String description) {
        Channel channel = channelCreationService.createChannel(space, name, description);
        List<User> users = space.getMembers().stream()
                .map(SpaceMember::getUser)
                .toList();
        Conversation conversation =
                conversationService.createNewConversationForUsers(ConversationType.CHANNEL, channel, users);
        channel.setConversation(conversation);
        channelRepository.save(channel);
        return channel;
    }
}