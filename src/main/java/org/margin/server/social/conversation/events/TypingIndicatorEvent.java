package org.margin.server.social.conversation.events;

import lombok.Getter;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class TypingIndicatorEvent extends ApplicationEvent {
    private final Long conversationId;
    private final User user;
    private final boolean isTyping;
    private final List<User> recipients;

    public TypingIndicatorEvent(Long conversationId, User user, boolean isTyping, List<User> recipients) {
        super(conversationId);
        this.conversationId = conversationId;
        this.user = user;
        this.isTyping = isTyping;
        this.recipients = recipients;
    }
}
