package org.margin.server.social.services;

import org.margin.server.social.models.SpaceChannel;
import org.margin.server.social.repositories.SpaceChannelRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpaceChannelService {
    private final SpaceChannelRepository spaceChannelRepository;

    public SpaceChannelService(SpaceChannelRepository spaceChannelRepository) {
        this.spaceChannelRepository = spaceChannelRepository;
    }

    public List<SpaceChannel> getChannelsForSpace(Long spaceId) {
        return spaceChannelRepository.getSpaceChannelsBySpaceId(spaceId);
    }
}
