package org.margin.server.websocket.models.payloads;

public record MessageReactionPayload(Long messageId, String emoji) {
}
