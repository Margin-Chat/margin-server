package org.margin.server.websocket.models.payloads;

import org.margin.server.users.models.dtos.UserDTO;

public record CallOfferPayload(
        Long callId,
        UserDTO caller,
        String sdp,
        String callType
) {
}