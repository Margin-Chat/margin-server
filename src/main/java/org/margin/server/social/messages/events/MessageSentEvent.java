package org.margin.server.social.messages.events;

import lombok.Getter;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class MessageSentEvent extends ApplicationEvent {
    private final MessageDTO message;
    private final List<User> recipients;

    public MessageSentEvent(MessageDTO message, List<User> recipients) {
        super(message);
        this.message = message;
        this.recipients = recipients;
    }
}