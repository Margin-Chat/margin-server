package org.margin.server.websocket.listeners;

import org.margin.server.sfu.events.ChannelCallInviteEvent;
import org.margin.server.sfu.events.ChannelVoiceParticipantEvent;
import org.margin.server.sfu.models.VoiceParticipantChange;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.users.models.User;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SfuWebSocketEventListener {

    private final WebSocketMessageBuilder messageBuilder;
    private final ConnectionManager connectionManager;
    private final SpaceMemberRepository spaceMemberRepository;

    public SfuWebSocketEventListener(WebSocketMessageBuilder messageBuilder,
                                     ConnectionManager connectionManager,
                                     SpaceMemberRepository spaceMemberRepository) {
        this.messageBuilder = messageBuilder;
        this.connectionManager = connectionManager;
        this.spaceMemberRepository = spaceMemberRepository;
    }

    @EventListener
    public void onChannelCallInvite(ChannelCallInviteEvent event) {
        String json = messageBuilder.buildMessage(
                WebSocketMessageType.CHANNEL_CALL_INVITE, event.getRecipientId(), event.getPayload());
        connectionManager.sendToUser(event.getRecipientId(), json);
    }

    @EventListener
    public void onChannelVoiceParticipantChanged(ChannelVoiceParticipantEvent event) {
        String json = messageBuilder.buildMessage(toMessageType(event.getChange()), event.getPayload().channelId(), event.getPayload());
        List<User> members = spaceMemberRepository.findSpaceMemberByChannel_Id(event.getChannelId());
        for (User member : members) {
            if (connectionManager.isUserOnline(member.getId())) {
                connectionManager.sendToUser(member.getId(), json);
            }
        }
    }

    private static WebSocketMessageType toMessageType(VoiceParticipantChange change) {
        return switch (change) {
            case JOINED -> WebSocketMessageType.USER_JOINED_VOICE;
            case LEFT -> WebSocketMessageType.USER_LEFT_VOICE;
        };
    }
}
