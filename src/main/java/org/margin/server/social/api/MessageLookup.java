package org.margin.server.social.api;

import java.util.List;

public interface MessageLookup {

    MessageContext contextOf(Long messageId);

    List<Long> threadFollowerIds(Long conversationId);

    record MessageContext(Long authorId, Long conversationId, Long marginId) {
    }
}
