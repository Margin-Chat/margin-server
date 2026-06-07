package org.margin.server.social.messages.events;

import lombok.Getter;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.springframework.context.ApplicationEvent;

@Getter
public class MessageDeletedEvent extends ApplicationEvent {
    private final MessageResult messageResult;

    public MessageDeletedEvent(MessageResult messageResult) {
        super(messageResult);
        this.messageResult = messageResult;
    }
}