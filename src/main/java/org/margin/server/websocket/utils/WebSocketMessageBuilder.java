package org.margin.server.websocket.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.websocket.models.WebSocketMessage;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.CallOfferPayload;
import org.margin.server.websocket.models.payloads.CallResponsePayload;
import org.margin.server.websocket.models.payloads.IncomingCallCandidatePayload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebSocketMessageBuilder {

    private final ObjectMapper mapper;

    public WebSocketMessageBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String userActivity(WebSocketMessageType type, User user) {
        return buildMessage(type, user.getId(), new UserDTO(user));
    }

    public String message(MessageDTO message) {
        return buildMessage(
                WebSocketMessageType.RECEIVE_MESSAGE,
                message.conversationId(),
                message
        );
    }

    public String callOffer(Long callId, Long recipientId, String sdp, String callType) {
        CallOfferPayload payload = new CallOfferPayload(callId, recipientId, sdp, callType);
        return buildMessage(WebSocketMessageType.CALL_OFFER, recipientId, payload);
    }

    public String callResponse(Long recipientId, CallResponsePayload payload) {
        return buildMessage(WebSocketMessageType.CALL_RESPONSE, recipientId, payload);
    }

    public String callCandidate(Long recipientId, IncomingCallCandidatePayload payload) {
        return buildMessage(WebSocketMessageType.CALL_CANDIDATE, recipientId, payload);
    }

    public String callEnd(Long recipientId) {
        return buildMessage(WebSocketMessageType.CALL_END, recipientId, null);
    }

    private <T> String buildMessage(WebSocketMessageType type, Long recipientId, T payload) {
        WebSocketMessage<T> message = new WebSocketMessage<>(
                type,
                System.currentTimeMillis(),
                recipientId,
                payload
        );
        return toJson(message);
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WebSocket message", e);
            throw new RuntimeException("Failed to serialize WebSocket message", e);
        }
    }
}