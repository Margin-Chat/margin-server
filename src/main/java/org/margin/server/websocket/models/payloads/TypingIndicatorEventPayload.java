package org.margin.server.websocket.models.payloads;

public record TypingIndicatorEventPayload(Long userId, String displayName, boolean isTyping) {
}
