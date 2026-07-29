package org.margin.server.websocket.processors;

import org.margin.server.sfu.services.SfuService;
import org.margin.server.social.api.ChannelDirectory;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;

@Component
public class ChannelCallInviteProcessor implements WebSocketMessageProcessor<String> {

    private final SfuService sfuService;
    private final MarginAccessChecker marginAccessChecker;
    private final ChannelDirectory channelDirectory;

    public ChannelCallInviteProcessor(SfuService sfuService,
                                      MarginAccessChecker marginAccessChecker,
                                      ChannelDirectory channelDirectory) {
        this.sfuService = sfuService;
        this.marginAccessChecker = marginAccessChecker;
        this.channelDirectory = channelDirectory;
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

        sfuService.inviteToChannelCall(user.getId(), recipientId, channelId, channelDirectory.nameOf(channelId));
    }
}
