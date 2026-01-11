package org.margin.server.social.controllers;

import org.margin.server.social.models.SpaceChannelDTO;
import org.margin.server.social.services.SpaceChannelService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/channels")
public class SpaceChannelController {
    private final SpaceChannelService spaceChannelService;

    public SpaceChannelController(SpaceChannelService spaceChannelService) {
        this.spaceChannelService = spaceChannelService;
    }

    @GetMapping("/{spaceId}/get_channels_for_space")
    public List<SpaceChannelDTO> getChannelsForSpace(@PathVariable Long spaceId) {
        return spaceChannelService.getChannelsForSpace(spaceId)
                .stream()
                .map(SpaceChannelDTO::new)
                .toList();
    }
}
