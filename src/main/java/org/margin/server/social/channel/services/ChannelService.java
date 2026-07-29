package org.margin.server.social.channel.services;

import org.margin.server.social.api.ChannelDirectory;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.exceptions.ChannelNotFoundException;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.channel.models.ChannelType;
import org.margin.server.social.channel.ChannelLookup;
import org.margin.server.social.channel.repositories.ChannelRepository;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.api.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.space.exceptions.SpaceNotFoundException;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.repositories.SpacesRepository;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ChannelService implements ChannelLookup, ChannelDirectory {
    private final ChannelCreationService channelCreationService;
    private final ConversationService conversationService;
    private final ChannelRepository channelRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MarginMapper marginMapper;
    private final SpacesRepository spacesRepository;

    public ChannelService(ChannelCreationService channelCreationService,
                          ConversationService conversationService,
                          ChannelRepository channelRepository,
                          ConversationMemberRepository conversationMemberRepository,
                          MarginMapper marginMapper, SpacesRepository spacesRepository) {
        this.channelCreationService = channelCreationService;
        this.conversationService = conversationService;
        this.channelRepository = channelRepository;
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
        return createChannelAsDto(spaceId, name, description, ChannelType.Communication);
    }

    @Transactional
    public ChannelDTO createChannelAsDto(Long spaceId, String name, String description, ChannelType channelType) {
        Space space = spacesRepository.findById(spaceId).orElseThrow(() -> new SpaceNotFoundException(spaceId));
        Channel channel = channelCreationService.createChannel(space, name, description, channelType);
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
        channel.setUpdatedAt(Instant.now());
        return marginMapper.channelToDto(channelRepository.save(channel));
    }

    @Transactional
    public void deleteChannel(Long channelId) {
        Channel channel = getById(channelId);
        Conversation conversation = channel.getConversation();
        Instant now = Instant.now();

        conversationMemberRepository.deleteAll(conversationMemberRepository.findByConversation(conversation));
        conversation.setDeletedAt(now);
        channel.setDeletedAt(now);
        channelRepository.save(channel);
    }

    @Override
    public Channel getById(Long channelId) {
        return channelRepository.findById(channelId).orElseThrow(() -> new ChannelNotFoundException(channelId));
    }

    @Transactional
    public void removeUserFromChannels(Long userId, List<Channel> channels) {
        for (Channel channel : channels) {
            conversationService.removeMember(channel.getConversation().getId(), userId);
        }
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

    @Override
    @Transactional(readOnly = true)
    public Long marginIdOf(Long channelId) {
        return getById(channelId).getSpace().getMargin().getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Long conversationIdOf(Long channelId) {
        return getById(channelId).getConversation().getId();
    }

    @Override
    @Transactional(readOnly = true)
    public String nameOf(Long channelId) {
        return getById(channelId).getName();
    }
}