package org.margin.server.social.messages.events;

import lombok.Getter;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.messages.models.dtos.MessageReactionDTO;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class ReactionAddedEvent extends ApplicationEvent {
    private final MessageReactionDTO reaction;
    private final List<User> recipients;
    private final ConversationType conversationType;

    public ReactionAddedEvent(MessageReactionDTO reaction, List<User> recipients, ConversationType conversationType) {
        super(reaction);
        this.reaction = reaction;
        this.recipients = recipients;
        this.conversationType = conversationType;
    }
}