package org.margin.server.social.conversation.events;

import lombok.Getter;
import org.margin.server.social.conversation.models.dtos.DirectConversationDTO;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

@Getter
public class ConversationInviteEvent extends ApplicationEvent {
    private final DirectConversationDTO conversation;
    private final User sender;
    private final Long recipientId;

    public ConversationInviteEvent(DirectConversationDTO conversation, User sender, Long recipientId) {
        super(conversation);
        this.conversation = conversation;
        this.sender = sender;
        this.recipientId = recipientId;
    }
}