package org.margin.server.social.channel;

import org.margin.server.social.channel.channel.Channel;
import org.margin.server.social.conversation.Conversation;
import org.margin.server.social.conversation.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.space.services.SpacesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChannelService {
    private final ChannelRepository channelRepository;
    private final ConversationRepository conversationRepository;
    private final SpacesService spacesService;

    public ChannelService(ChannelRepository channelRepository,
                          ConversationRepository conversationRepository,
                          SpacesService spacesService) {
        this.channelRepository = channelRepository;
        this.conversationRepository = conversationRepository;
        this.spacesService = spacesService;
    }

    public List<Channel> getChannelsForSpace(Long spaceId) {
        return channelRepository.getChannelsBySpaceId(spaceId);
    }

    @Transactional
    public void createChannel(Long spaceId, String name, String description) {
        Channel channel = new Channel();
        channel.setSpace(spacesService.getById(spaceId));
        channel.setName(name);
        channel.setDescription(description);
        channel = channelRepository.save(channel);

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.CHANNEL);
        conversation.setChannel(channel);
        conversation.setName(name);
        conversation.setCreatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

    }

    public Channel getById(Long id) {
        return channelRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Channel not found"));
    }
}