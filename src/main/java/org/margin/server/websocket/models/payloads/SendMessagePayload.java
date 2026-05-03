package org.margin.server.websocket.models.payloads;

public record SendMessagePayload(String content, String imageAddress) {
}
