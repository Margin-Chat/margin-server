package org.margin.server.social.conversation.events;

import lombok.Getter;
import org.margin.server.social.conversation.models.dtos.ConversationDTO;
import org.margin.server.users.api.UserSummary;
import org.springframework.context.ApplicationEvent;

@Getter
public class ConversationInviteEvent extends ApplicationEvent {
    private final ConversationDTO conversation;
    private final UserSummary sender;
    private final Long recipientId;

    public ConversationInviteEvent(ConversationDTO conversation, UserSummary sender, Long recipientId) {
        super(conversation);
        this.conversation = conversation;
        this.sender = sender;
        this.recipientId = recipientId;
    }
}
