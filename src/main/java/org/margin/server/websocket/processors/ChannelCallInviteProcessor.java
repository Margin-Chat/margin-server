package org.margin.server.websocket.processors;

import org.margin.server.sfu.services.SfuService;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;

@Component
public class ChannelCallInviteProcessor implements WebSocketMessageProcessor<String> {

    private final SfuService sfuService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final ChannelService channelService;

    public ChannelCallInviteProcessor(SfuService sfuService,
                                      MarginAuthorizationService marginAuthorizationService,
                                      ChannelService channelService) {
        this.sfuService = sfuService;
        this.marginAuthorizationService = marginAuthorizationService;
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

        marginAuthorizationService.requireChannelMember(user.getId(), channelId);
        marginAuthorizationService.requireChannelMember(recipientId, channelId);

        Channel channel = channelService.getById(channelId);
        sfuService.inviteToChannelCall(user, recipientId, channelId, channel.getName());
    }
}
