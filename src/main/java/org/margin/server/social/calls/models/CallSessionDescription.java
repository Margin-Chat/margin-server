package org.margin.server.social.calls.models;

public record CallSessionDescription(
        String sdp,
        String type
) {
}