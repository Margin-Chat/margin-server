package org.margin.server.websocket.processors;

import org.margin.server.sfu.services.SfuService;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;

@Component
public class ChannelCallInviteProcessor implements WebSocketMessageProcessor<String> {

    private final SfuService sfuService;
    private final MarginAccessChecker marginAccessChecker;
    private final ChannelService channelService;

    public ChannelCallInviteProcessor(SfuService sfuService,
                                      MarginAccessChecker marginAccessChecker,
                                      ChannelService channelService) {
        this.sfuService = sfuService;
        this.marginAccessChecker = marginAccessChecker;
        this.channelService = channelService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CHANNEL_CALL_INVITE;
    }

    @Override
    public void process(User user, WebSocketMessageIn<String> message) {
        Long channelId = Long.valueOf(message.getPayload());
        Long recipientId = message.getRecipientId();

        marginAccessChecker.requireChannelMember(user.getId(), channelId);
        marginAccessChecker.requireChannelMember(recipientId, channelId);

        Channel channel = channelService.getById(channelId);
        sfuService.inviteToChannelCall(user, recipientId, channelId, channel.getName());
    }
}
