package org.margin.server.websocket.processors;

import org.margin.server.sfu.services.SfuService;
import org.margin.server.social.api.ChannelLookup;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;

@Component
public class ChannelCallInviteProcessor implements WebSocketMessageProcessor<String> {

    private final SfuService sfuService;
    private final MarginAccessChecker marginAccessChecker;
    private final ChannelLookup channelLookup;

    public ChannelCallInviteProcessor(SfuService sfuService,
                                      MarginAccessChecker marginAccessChecker,
                                      ChannelLookup channelLookup) {
        this.sfuService = sfuService;
        this.marginAccessChecker = marginAccessChecker;
        this.channelLookup = channelLookup;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CHANNEL_CALL_INVITE;
    }

    @Override
    public void process(AuthenticatedUser user, WebSocketMessageIn<String> message) {
        Long channelId = Long.valueOf(message.getPayload());
        Long recipientId = message.getRecipientId();

        marginAccessChecker.requireChannelMember(user.id(), channelId);
        marginAccessChecker.requireChannelMember(recipientId, channelId);

        sfuService.inviteToChannelCall(user.id(), recipientId, channelId, channelLookup.nameOf(channelId));
    }
}
