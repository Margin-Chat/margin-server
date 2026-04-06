package org.margin.server.websocket.models.payloads;

import java.time.Instant;

public record ConversationReadPayload(Long conversationId, Instant readAt) {
}
