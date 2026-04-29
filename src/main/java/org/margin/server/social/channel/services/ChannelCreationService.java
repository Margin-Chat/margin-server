package org.margin.server.social.channel.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.models.ChannelType;
import org.margin.server.social.channel.repositories.ChannelRepository;
import org.margin.server.social.space.models.Space;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
public class ChannelCreationService {
    private final ChannelRepository channelRepository;

    public ChannelCreationService(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    @Transactional
    public Channel createChannel(Space space, String name, String description) {
        Channel channel = new Channel();
        channel.setSpace(space);
        channel.setName(name);
        channel.setDescription(description);
        channel.setChannelType(ChannelType.Communication);
        channel.setCreatedAt(Instant.now());

        Channel savedChannel = channelRepository.save(channel);
        log.info("Creating channel: {}", name);
        space.getChannels().add(savedChannel);
        return savedChannel;
    }
}
