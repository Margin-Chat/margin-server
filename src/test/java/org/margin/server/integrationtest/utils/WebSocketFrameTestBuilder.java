package org.margin.server.integrationtest.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class WebSocketFrameTestBuilder {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ObjectNode root;

    private WebSocketFrameTestBuilder(String type) {
        this.root = objectMapper.createObjectNode();
        this.root.put("type", type);
    }

    public static WebSocketFrameTestBuilder ofType(String type) {
        return new WebSocketFrameTestBuilder(type);
    }

    public WebSocketFrameTestBuilder recipientId(long recipientId) {
        root.put("recipientId", recipientId);
        return this;
    }

    public WebSocketFrameTestBuilder payload(String payload) {
        root.put("payload", payload);
        return this;
    }

    public WebSocketFrameTestBuilder payload(ObjectNode payload) {
        root.set("payload", payload);
        return this;
    }

    public static ObjectNode object() {
        return objectMapper.createObjectNode();
    }

    public String build() {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build WebSocket frame", e);
        }
    }
}