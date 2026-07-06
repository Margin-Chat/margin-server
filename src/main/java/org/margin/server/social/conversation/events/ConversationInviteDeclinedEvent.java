package org.margin.server.social.conversation.events;

import lombok.Getter;
import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.context.ApplicationEvent;

@Getter
public class ConversationInviteDeclinedEvent extends ApplicationEvent {
    private final Long conversationId;
    private final UserDTO declinedBy;
    private final Long recipientId;

    public ConversationInviteDeclinedEvent(Long conversationId, UserDTO declinedBy, Long recipientId) {
        super(conversationId);
        this.conversationId = conversationId;
        this.declinedBy = declinedBy;
        this.recipientId = recipientId;
    }
}
