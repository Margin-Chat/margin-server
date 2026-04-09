package org.margin.server.social.channel.controllers;

import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.users.models.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {
    private final ChannelService channelService;
    private final MarginAuthorizationService marginAuthorizationService;

    public ChannelController(ChannelService channelService,
                             MarginAuthorizationService marginAuthorizationService) {
        this.channelService = channelService;
        this.marginAuthorizationService = marginAuthorizationService;
    }

    @GetMapping("/{spaceId}/get_channels_for_space")
    public List<ChannelDTO> getChannelsForSpace(@AuthenticationPrincipal User user,
                                                @PathVariable Long spaceId) {
        marginAuthorizationService.requireSpaceMember(user.getId(), spaceId);
        return channelService.getChannelsForSpaceAsDto(spaceId);
    }

    @PostMapping("/create_channel")
    public ChannelDTO createChannel(@AuthenticationPrincipal User user,
                                    @RequestBody ChannelDTO channelDTO) {
        marginAuthorizationService.requireSpaceAdmin(user.getId(), channelDTO.spaceId());
        return channelService.createChannelAsDto(channelDTO.spaceId(), channelDTO.name(), channelDTO.description());
    }

    @PostMapping("/update_channel")
    public ChannelDTO updateChannel(@AuthenticationPrincipal User user,
                                    @RequestBody ChannelDTO channelDTO) {
        marginAuthorizationService.requireSpaceAdmin(user.getId(), channelDTO.spaceId());
        return channelService.updateChannelAsDto(channelDTO);
    }

    @DeleteMapping("/delete_channel")
    public ResponseEntity<Void> deleteChannel(@AuthenticationPrincipal User user,
                                              @RequestBody Long channelId) {
        Channel channel = channelService.getById(channelId);
        marginAuthorizationService.requireSpaceAdmin(user.getId(), channel.getSpace().getId());
        channelService.deleteChannel(channelId);
        return ResponseEntity.ok().build();
    }
}