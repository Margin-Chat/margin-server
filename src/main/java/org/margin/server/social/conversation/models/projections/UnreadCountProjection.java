package org.margin.server.social.conversation.models.projections;

public interface UnreadCountProjection {
    Long getConversationId();

    Long getUnreadDmsCount();

    Long getUnreadChannelsCount();

    Long getMarginId();
}