package org.margin.server.social.services;

import org.margin.server.social.communication.messages.models.dtos.ChannelMessageDTO;
import org.margin.server.social.models.channel.Channel;
import org.margin.server.social.communication.messages.repositories.ChannelMessageRepository;
import org.margin.server.social.repositories.ChannelRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChannelService {
    private final ChannelRepository channelRepository;
    private final ChannelMessageRepository channelMessageRepository;

    public ChannelService(ChannelRepository channelRepository,
                          ChannelMessageRepository channelMessageRepository) {
        this.channelRepository = channelRepository;
        this.channelMessageRepository = channelMessageRepository;
    }

    public List<Channel> getChannelsForSpace(Long spaceId) {
        return channelRepository.getChannelsBySpaceId(spaceId);
    }

    public List<ChannelMessageDTO> getMessagesForChannel(Long channelId) {
        return channelMessageRepository.getChannelMessageByChannelId(channelId).stream()
                .map(m -> ChannelMessageDTO.fromEntity(m,
                        m.getFromUser().getUsername()))
                .collect(Collectors.toList());
    }


    public Channel getById(Long id) {
        return channelRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
