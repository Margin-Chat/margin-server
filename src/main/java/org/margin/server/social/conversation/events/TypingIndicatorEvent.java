package org.margin.server.social.conversation.events;

import lombok.Getter;
import org.margin.server.users.api.UserSummary;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class TypingIndicatorEvent extends ApplicationEvent {
    private final Long conversationId;
    private final UserSummary user;
    private final boolean isTyping;
    private final List<Long> recipientIds;

    public TypingIndicatorEvent(Long conversationId, UserSummary user, boolean isTyping, List<Long> recipientIds) {
        super(conversationId);
        this.conversationId = conversationId;
        this.user = user;
        this.isTyping = isTyping;
        this.recipientIds = recipientIds;
    }
}
