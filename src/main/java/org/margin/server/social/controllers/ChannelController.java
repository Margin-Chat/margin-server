package org.margin.server.social.controllers;

import org.margin.server.social.models.channel.ChannelDTO;
import org.margin.server.social.services.ChannelService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/channels")
public class ChannelController {
    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping("/{spaceId}/get_channels_for_space")
    public List<ChannelDTO> getChannelsForSpace(@PathVariable Long spaceId) {
        return channelService.getChannelsForSpace(spaceId)
                .stream()
                .map(ChannelDTO::new)
                .toList();
    }
}
