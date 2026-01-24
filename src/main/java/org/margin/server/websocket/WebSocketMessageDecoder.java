package org.margin.server.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.IncomingCallCandidatePayload;
import org.margin.server.websocket.models.payloads.IncomingCallEndPayload;
import org.margin.server.websocket.models.payloads.IncomingCallOfferPayload;
import org.margin.server.websocket.models.payloads.IncomingCallResponsePayload;

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
            WebSocketMessageType type = WebSocketMessageType.valueOf(
                    node.get("type").asText()
            );

            WebSocketMessageIn<?> message = switch (type) {
                case SEND_DIRECT_MESSAGE, SEND_CHANNEL_MESSAGE -> parseMessage(payload, String.class);
                case CALL_OFFER -> parseMessage(payload, IncomingCallOfferPayload.class);
                case CALL_RESPONSE -> parseMessage(payload, IncomingCallResponsePayload.class);
                case CALL_CANDIDATE -> parseMessage(payload, IncomingCallCandidatePayload.class);
                case CALL_END -> parseMessage(payload, IncomingCallEndPayload.class);
                default -> throw new IllegalStateException("Unexpected value: " + type);
            };

            out.add(message);

        } catch (IllegalArgumentException e) {
            ctx.fireExceptionCaught(new IllegalArgumentException("Unknown message type: " + e.getMessage()));
        } catch (JsonProcessingException e) {
            ctx.fireExceptionCaught(new RuntimeException("Failed to parse message: " + e.getMessage()));
        }
    }

    private <T> WebSocketMessageIn<T> parseMessage(String json, Class<T> payloadClass) throws JsonProcessingException {
        JavaType type = objectMapper.getTypeFactory()
                .constructParametricType(WebSocketMessageIn.class, payloadClass);
        return objectMapper.readValue(json, type);
    }
}
