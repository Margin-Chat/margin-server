package org.margin.server.social.messages.events;

import lombok.Getter;
import org.margin.server.social.api.ConversationType;
import org.margin.server.social.messages.models.dtos.MessageReactionDTO;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class ReactionAddedEvent extends ApplicationEvent {
    private final MessageReactionDTO reaction;
    private final List<Long> recipientIds;
    private final ConversationType conversationType;

    public ReactionAddedEvent(MessageReactionDTO reaction, List<Long> recipientIds, ConversationType conversationType) {
        super(reaction);
        this.reaction = reaction;
        this.recipientIds = recipientIds;
        this.conversationType = conversationType;
    }
}