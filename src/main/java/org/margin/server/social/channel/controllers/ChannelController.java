package org.margin.server.social.channel.controllers;

import org.margin.server.shared.security.AuthenticatedUser;
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

    @GetMapping("/{channelId}")
    public ResponseEntity<ChannelDTO> getChannel(@PathVariable Long channelId,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        Channel channel = channelService.getById(channelId);
        marginAuthorizationService.requireMarginMember(user.id(), channel.getSpace().getMargin().getId());
        return ResponseEntity.ok(new ChannelDTO(channel));
    }

    @GetMapping("/{spaceId}/get_channels_for_space")
    public List<ChannelDTO> getChannelsForSpace(@AuthenticationPrincipal AuthenticatedUser user,
                                                @PathVariable Long spaceId) {
        marginAuthorizationService.requireSpaceMember(user.id(), spaceId);
        return channelService.getChannelsForSpaceAsDto(spaceId);
    }

    @PostMapping("/create_channel")
    public ChannelDTO createChannel(@AuthenticationPrincipal AuthenticatedUser user,
                                    @RequestBody ChannelDTO channelDTO) {
        marginAuthorizationService.requireSpaceAdmin(user.id(), channelDTO.spaceId());
        return channelService.createChannelAsDto(channelDTO.spaceId(), channelDTO.name(),
                channelDTO.description(), channelDTO.channelType());
    }

    @PostMapping("/update_channel")
    public ChannelDTO updateChannel(@AuthenticationPrincipal AuthenticatedUser user,
                                    @RequestBody ChannelDTO channelDTO) {
        marginAuthorizationService.requireSpaceAdmin(user.id(), channelDTO.spaceId());
        return channelService.updateChannelAsDto(channelDTO);
    }

    @DeleteMapping("/delete_channel")
    public ResponseEntity<Void> deleteChannel(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestBody Long channelId) {
        Channel channel = channelService.getById(channelId);
        marginAuthorizationService.requireSpaceAdmin(user.id(), channel.getSpace().getId());
        channelService.deleteChannel(channelId);
        return ResponseEntity.ok().build();
    }
}