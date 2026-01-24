package org.margin.server.websocket.models.payloads;

public record CallResponsePayload(
        Long callId,
        Long callerId,
        CallSessionDescription response
) {}