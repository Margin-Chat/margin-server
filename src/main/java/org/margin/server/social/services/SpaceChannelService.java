package org.margin.server.social.services;

import org.margin.server.social.communication.messages.models.dtos.SpaceChannelMessageDTO;
import org.margin.server.social.models.SpaceChannel;
import org.margin.server.social.communication.messages.models.SpaceChannelMessage;
import org.margin.server.social.communication.messages.repositories.SpaceChannelMessageRepository;
import org.margin.server.social.repositories.SpaceChannelRepository;
import org.margin.server.users.services.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SpaceChannelService {
    private final SpaceChannelRepository spaceChannelRepository;
    private final SpaceChannelMessageRepository spaceChannelMessageRepository;
    private final UserService userService;

    public SpaceChannelService(SpaceChannelRepository spaceChannelRepository,
                               SpaceChannelMessageRepository spaceChannelMessageRepository,
                               UserService userService) {
        this.spaceChannelRepository = spaceChannelRepository;
        this.spaceChannelMessageRepository = spaceChannelMessageRepository;
        this.userService = userService;
    }

    public List<SpaceChannel> getChannelsForSpace(Long spaceId) {
        return spaceChannelRepository.getSpaceChannelsBySpaceId(spaceId);
    }

    public List<SpaceChannelMessageDTO> getMessagesForChannel(Long channelId) {
        return spaceChannelMessageRepository.getSpaceChannelMessageByChannelId(channelId).stream()
                .map(m -> SpaceChannelMessageDTO.fromEntity(m,
                        userService.getById(m.getFromUserId()).getUsername()))
                .collect(Collectors.toList());
    }
}
