package org.margin.server.social.messages.events;

import lombok.Getter;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class MessageEditedEvent extends ApplicationEvent {
    private final MessageDTO message;
    private final List<User> recipients;
    private final ConversationType conversationType;

    public MessageEditedEvent(MessageDTO message, List<User> recipients, ConversationType conversationType) {
        super(message);
        this.message = message;
        this.recipients = recipients;
        this.conversationType = conversationType;
    }
}