package org.margin.server.websocket.models.payloads;

public record CallOfferPayload(
        Long callId,
        Long callerId,
        String sdp,
        String callType
) {
}