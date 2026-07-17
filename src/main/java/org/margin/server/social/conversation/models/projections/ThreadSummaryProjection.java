package org.margin.server.social.conversation.models.projections;

import java.time.Instant;

public interface ThreadSummaryProjection {
    Long getThreadConversationId();

    Long getMessageCount();

    Instant getLastReplyAt();

    Instant getLastOtherReplyAt();

    Long getFirstMessageId();
}
