package org.margin.server.websocket.models.payloads;

public record IncomingCallResponsePayload(
        Long callId,
        Long callerId,
        CallSessionDescription response
) {
}