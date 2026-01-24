package org.margin.server.websocket.models.payloads;

public record IncomingCallEndPayload(
        Long callId,
        Integer callDuration
) {
}
