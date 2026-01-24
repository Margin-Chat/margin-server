package org.margin.server.social.services;

import org.margin.server.social.models.SpaceChannel;
import org.margin.server.social.models.SpaceChannelMessage;
import org.margin.server.social.communication.messages.repositories.SpaceChannelMessageRepository;
import org.margin.server.social.repositories.SpaceChannelRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpaceChannelService {
    private final SpaceChannelRepository spaceChannelRepository;
    private final SpaceChannelMessageRepository spaceChannelMessageRepository;

    public SpaceChannelService(SpaceChannelRepository spaceChannelRepository,
                               SpaceChannelMessageRepository spaceChannelMessageRepository) {
        this.spaceChannelRepository = spaceChannelRepository;
        this.spaceChannelMessageRepository = spaceChannelMessageRepository;
    }

    public List<SpaceChannel> getChannelsForSpace(Long spaceId) {
        return spaceChannelRepository.getSpaceChannelsBySpaceId(spaceId);
    }

    public List<SpaceChannelMessage> getMessagesForChannel(Long channelId) {
        return spaceChannelMessageRepository.getSpaceChannelMessageByChannelId(channelId);
    }
}
