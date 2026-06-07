package org.margin.server.social.conversation.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

@Getter
public class ConversationReadEvent extends ApplicationEvent {
    private final Long conversationId;
    private final Long recipientId;
    private final Instant readAt;

    public ConversationReadEvent(Long conversationId, Long recipientId, Instant readAt) {
        super(conversationId);
        this.conversationId = conversationId;
        this.recipientId = recipientId;
        this.readAt = readAt;
    }
}