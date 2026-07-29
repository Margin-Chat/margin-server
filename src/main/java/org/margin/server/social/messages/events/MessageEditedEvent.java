package org.margin.server.social.messages.events;

import lombok.Getter;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class MessageEditedEvent extends ApplicationEvent {
    private final MessageDTO message;
    private final List<Long> recipientIds;
    private final ConversationType conversationType;

    public MessageEditedEvent(MessageDTO message, List<Long> recipientIds, ConversationType conversationType) {
        super(message);
        this.message = message;
        this.recipientIds = recipientIds;
        this.conversationType = conversationType;
    }
}