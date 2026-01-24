package org.margin.server.websocket.models.payloads;

public record IncomingCallOfferPayload(
        String sdp,
        String type
) {
}
