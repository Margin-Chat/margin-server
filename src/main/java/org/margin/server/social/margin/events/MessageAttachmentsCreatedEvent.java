package org.margin.server.social.margin.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class MessageAttachmentsCreatedEvent extends ApplicationEvent {
    private final Long messageId;
    private final Long senderId;
    private final Long channelId;
    private final List<Long> attachmentIds;

    public MessageAttachmentsCreatedEvent(Long messageId, Long senderId, Long channelId, List<Long> attachmentIds) {
        super(messageId);
        this.messageId = messageId;
        this.senderId = senderId;
        this.channelId = channelId;
        this.attachmentIds = attachmentIds;
    }
}