package org.margin.server.social.channel;

import org.margin.server.social.channel.channel.ChannelDTO;
import org.margin.server.social.conversation.Conversation;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.services.ConversationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
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

    @PostMapping("/create_channel")
    public ChannelDTO createChannel(@RequestBody ChannelDTO channelDTO) {
        return new ChannelDTO(channelService.createChannel(
                channelDTO.spaceId(),
                channelDTO.name(),
                channelDTO.description()));
    }

    @PostMapping("/update_channel")
    public ResponseEntity<ChannelDTO> updateChannel(@RequestBody ChannelDTO channelDTO) {
        return ResponseEntity.ok(new ChannelDTO(channelService.updateChannel(channelDTO)));
    }

    @PostMapping("/delete_channel")
    public ResponseEntity<Void> deleteChannel(@RequestBody Long channelId) {
        channelService.deleteChannel(channelId);
        return ResponseEntity.ok().build();
    }
}
