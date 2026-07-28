package org.margin.server.social.calls.models;

public record CallCandidate(
        Long callId,
        String candidate,
        String sdpMid,
        Integer sdpMLineIndex,
        String handleFragment
) {
}