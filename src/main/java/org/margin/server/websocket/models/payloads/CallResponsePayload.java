package org.margin.server.websocket.models.payloads;

import org.margin.server.social.calls.models.CallSessionDescription;

public record CallResponsePayload(
        Long callId,
        Long callerId,
        CallSessionDescription response
) {}