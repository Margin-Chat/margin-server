package org.margin.server.websocket.models.payloads;

import java.util.List;

public record SendMessagePayload(String content, List<Long> attachmentIds) {
}
