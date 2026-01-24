package org.margin.server.websocket.models.payloads;

public record CallSessionDescription(
        String sdp,
        String type
) {
}