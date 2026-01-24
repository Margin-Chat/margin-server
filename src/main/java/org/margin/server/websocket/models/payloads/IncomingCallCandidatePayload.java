package org.margin.server.websocket.models.payloads;

public record IncomingCallCandidatePayload(
        Long callId,
        String candidate,
        String sdpMid,
        Integer sdpMLineIndex,
        String usernameFragment
) {
}