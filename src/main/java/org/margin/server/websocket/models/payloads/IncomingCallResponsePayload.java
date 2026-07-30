package org.margin.server.websocket.models.payloads;

import org.margin.server.social.calls.models.CallSessionDescription;

public record IncomingCallResponsePayload(
        Long callId,
        Long callerId,
        CallSessionDescription response
) {
}