package org.margin.server.social.conversation.events;

import lombok.Getter;
import org.margin.server.social.conversation.models.dtos.ConversationDTO;
import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.context.ApplicationEvent;

@Getter
public class ConversationInviteAcceptedEvent extends ApplicationEvent {
    private final ConversationDTO conversation;
    private final UserDTO acceptedBy;
    private final Long recipientId;

    public ConversationInviteAcceptedEvent(ConversationDTO conversation, UserDTO acceptedBy, Long recipientId) {
        super(conversation);
        this.conversation = conversation;
        this.acceptedBy = acceptedBy;
        this.recipientId = recipientId;
    }
}