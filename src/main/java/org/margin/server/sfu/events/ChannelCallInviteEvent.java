package org.margin.server.sfu.events;

import lombok.Getter;
import org.margin.server.sfu.models.ChannelCallInvitePayload;
import org.springframework.context.ApplicationEvent;

@Getter
public class ChannelCallInviteEvent extends ApplicationEvent {
    private final Long recipientId;
    private final ChannelCallInvitePayload payload;

    public ChannelCallInviteEvent(Long recipientId, ChannelCallInvitePayload payload) {
        super(recipientId);
        this.recipientId = recipientId;
        this.payload = payload;
    }
}
