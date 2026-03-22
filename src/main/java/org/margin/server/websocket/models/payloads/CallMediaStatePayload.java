package org.margin.server.websocket.models.payloads;

public record CallMediaStatePayload(boolean video, boolean audio, boolean screenShare) {
}
