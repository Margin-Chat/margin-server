package org.margin.server.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class WebSocketMessageDecoder extends MessageToMessageDecoder<TextWebSocketFrame> {

    private final ObjectMapper objectMapper;

    public WebSocketMessageDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame frame, List<Object> out) {
        try {
            String payload = frame.text();
            JsonNode node = objectMapper.readTree(payload);
            WebSocketMessageType type = WebSocketMessageType.valueOf(node.get("type").asString());

            if (type.payloadClass == null) {
                Long recipientId = node.has("recipientId") ? node.get("recipientId").asLong() : null;
                out.add(new WebSocketMessageIn<>(type, recipientId, null));
                return;
            }

            out.add(parseMessage(payload, type.payloadClass));

        } catch (IllegalArgumentException e) {
            ctx.fireExceptionCaught(new IllegalArgumentException("Unknown message type: " + e.getMessage()));
        }
    }

    private <T> WebSocketMessageIn<T> parseMessage(String json, Class<T> payloadClass) {
        JavaType type = objectMapper.getTypeFactory()
                .constructParametricType(WebSocketMessageIn.class, payloadClass);
        return objectMapper.readValue(json, type);
    }
}
