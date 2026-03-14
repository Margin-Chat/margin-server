package org.margin.server.websocket.models.payloads;

public record EditMessagePayload(Long messageId,
                                 String content) {
}
