package org.margin.server.social.messages.events;

import lombok.Getter;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class MessageSentEvent extends ApplicationEvent {
    private final MessageDTO message;
    private final List<Long> recipientIds;

    public MessageSentEvent(MessageDTO message, List<Long> recipientIds) {
        super(message);
        this.message = message;
        this.recipientIds = recipientIds;
    }
}