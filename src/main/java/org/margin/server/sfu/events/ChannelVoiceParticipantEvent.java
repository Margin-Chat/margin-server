package org.margin.server.sfu.events;

import lombok.Getter;
import org.margin.server.sfu.models.ChannelVoiceParticipantPayload;
import org.margin.server.sfu.models.VoiceParticipantChange;
import org.springframework.context.ApplicationEvent;

@Getter
public class ChannelVoiceParticipantEvent extends ApplicationEvent {
    private final Long channelId;
    private final VoiceParticipantChange change;
    private final ChannelVoiceParticipantPayload payload;

    public ChannelVoiceParticipantEvent(Long channelId, VoiceParticipantChange change, ChannelVoiceParticipantPayload payload) {
        super(channelId);
        this.channelId = channelId;
        this.change = change;
        this.payload = payload;
    }
}
