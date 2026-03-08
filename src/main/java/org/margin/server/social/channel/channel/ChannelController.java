package org.margin.server.social.channel.channel;

import org.margin.server.social.channel.services.ChannelService;
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

    public ChannelController(ChannelService channelService, SpacesService spacesService) {
        this.channelService = channelService;
        this.spacesService = spacesService;
    }

    @GetMapping("/{spaceId}/get_channels_for_space")
    public List<ChannelDTO> getChannelsForSpace(@AuthenticationPrincipal User user,
                                                @PathVariable Long spaceId) {
        return channelService.getChannelsForSpace(spaceId)
                .stream()
                .map(ChannelDTO::new)
                .toList();
    }

    @PostMapping("/create_channel")
    public ChannelDTO createChannel(@AuthenticationPrincipal User user, @RequestBody ChannelDTO channelDTO) {
        return new ChannelDTO(channelService.createChannel(
                spacesService.getById(channelDTO.spaceId()),
                channelDTO.name(),
                channelDTO.description()));
    }

    @PostMapping("/update_channel")
    public ResponseEntity<ChannelDTO> updateChannel(@AuthenticationPrincipal User user,
                                                    @RequestBody ChannelDTO channelDTO) {
        return ResponseEntity.ok(new ChannelDTO(channelService.updateChannel(channelDTO)));
    }

    @PostMapping("/delete_channel")
    public ResponseEntity<Void> deleteChannel(@AuthenticationPrincipal User user,
                                              @RequestBody Long channelId) {
        channelService.deleteChannel(channelId);
        return ResponseEntity.ok().build();
    }
}
