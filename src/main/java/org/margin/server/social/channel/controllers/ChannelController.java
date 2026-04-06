package org.margin.server.social.channel.controllers;

import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.margin.service.MarginMapper;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.services.SpacesService;
import org.margin.server.users.models.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {
    private final ChannelService channelService;
    private final SpacesService spacesService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final MarginMapper marginMapper;

    public ChannelController(ChannelService channelService, SpacesService spacesService,
                             MarginAuthorizationService marginAuthorizationService,
                             MarginMapper marginMapper) {
        this.channelService = channelService;
        this.spacesService = spacesService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.marginMapper = marginMapper;
    }

    @GetMapping("/{spaceId}/get_channels_for_space")
    public List<ChannelDTO> getChannelsForSpace(@AuthenticationPrincipal User user,
                                                @PathVariable Long spaceId) {
        marginAuthorizationService.requireSpaceMember(user.getId(), spaceId);
        return channelService.getChannelsForSpace(spaceId)
                .stream()
                .map(marginMapper::channelToDto)
                .toList();
    }

    @PostMapping("/create_channel")
    public ChannelDTO createChannel(@AuthenticationPrincipal User user, @RequestBody ChannelDTO channelDTO) {
        Space space = spacesService.getById(channelDTO.spaceId());
        marginAuthorizationService.requireSpaceAdmin(user.getId(), space.getId());
        return marginMapper.channelToDto(channelService.createNewChannel(
                space,
                channelDTO.name(),
                channelDTO.description()));
    }

    @PostMapping("/update_channel")
    public ChannelDTO updateChannel(@AuthenticationPrincipal User user,
                                    @RequestBody ChannelDTO channelDTO) {
        Space space = spacesService.getById(channelDTO.spaceId());
        marginAuthorizationService.requireSpaceAdmin(user.getId(), space.getId());
        return marginMapper.channelToDto(channelService.updateChannel(channelDTO));
    }

    @DeleteMapping("/delete_channel")
    public ResponseEntity<Void> deleteChannel(@AuthenticationPrincipal User user,
                                              @RequestBody Long channelId) {
        Channel channel = channelService.getById(channelId);
        Space space = channel.getSpace();
        marginAuthorizationService.requireSpaceAdmin(user.getId(), space.getId());
        channelService.deleteChannel(channelId);
        return ResponseEntity.ok().build();
    }
}