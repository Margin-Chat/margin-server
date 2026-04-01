package org.margin.server.social.conversation.models.projections;

public interface UnreadConversationProjection {
    Long getConversationId();

    String getType();

    Long getMarginId();
}