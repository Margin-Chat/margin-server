package org.margin.server.sfu.events;

import lombok.Getter;
import org.margin.server.sfu.models.ChannelVoiceParticipantPayload;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.context.ApplicationEvent;

@Getter
public class ChannelVoiceParticipantEvent extends ApplicationEvent {
    private final Long channelId;
    private final WebSocketMessageType wsType;
    private final ChannelVoiceParticipantPayload payload;

    public ChannelVoiceParticipantEvent(Long channelId, WebSocketMessageType wsType, ChannelVoiceParticipantPayload payload) {
        super(channelId);
        this.channelId = channelId;
        this.wsType = wsType;
        this.payload = payload;
    }
}