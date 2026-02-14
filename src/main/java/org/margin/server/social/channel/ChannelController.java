package org.margin.server.social.channel;

import org.margin.server.social.channel.channel.ChannelDTO;
import org.margin.server.social.conversation.Conversation;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.services.ConversationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/channels")
public class ChannelController {
    private final ChannelService channelService;
    private final ConversationService conversationService;

    public ChannelController(ChannelService channelService,
                             ConversationService conversationService) {
        this.channelService = channelService;
        this.conversationService = conversationService;
    }

    @GetMapping("/{spaceId}/get_channels_for_space")
    public List<ChannelDTO> getChannelsForSpace(@PathVariable Long spaceId) {
        return channelService.getChannelsForSpace(spaceId)
                .stream()
                .map(c -> {
                    Conversation conversation = conversationService.getByChannelId(c.getId());
                    return new ChannelDTO(c, conversation);
                })
                .toList();
    }
}
