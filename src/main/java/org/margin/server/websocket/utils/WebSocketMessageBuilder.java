package org.margin.server.websocket.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.connection.ConnectionManager;
import org.margin.server.sfu.models.ChannelVoiceParticipantPayload;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.websocket.models.WebSocketMessage;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.CallOfferPayload;
import org.margin.server.websocket.models.payloads.CallResponsePayload;
import org.margin.server.websocket.models.payloads.IncomingCallCandidatePayload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class WebSocketMessageBuilder {

    private final ObjectMapper mapper;
    private final ConnectionManager connectionManager;

    public WebSocketMessageBuilder(ObjectMapper mapper, ConnectionManager connectionManager) {
        this.mapper = mapper;
        this.connectionManager = connectionManager;
    }

    public String userActivity(WebSocketMessageType type, User user) {
        return buildMessage(type, user.getId(), new UserDTO(
                user,
                connectionManager.isUserOnline(user.getId()))
        );
    }

    public String message(MessageDTO message) {
        return buildMessage(
                WebSocketMessageType.RECEIVE_MESSAGE,
                message.conversationId(),
                message
        );
    }

    public String callOffer(Long callId, UserDTO caller, String sdp, String callType) {
        CallOfferPayload payload = new CallOfferPayload(callId, caller, sdp, callType);
        return buildMessage(WebSocketMessageType.CALL_OFFER, caller.id(), payload);
    }

    public String callResponse(Long recipientId, CallResponsePayload payload) {
        return buildMessage(WebSocketMessageType.CALL_RESPONSE, recipientId, payload);
    }

    public String callCandidate(Long recipientId, IncomingCallCandidatePayload payload) {
        return buildMessage(WebSocketMessageType.CALL_CANDIDATE, recipientId, payload);
    }

    public String screenShareOffer(Long recipientId, String sdp, String type) {
        Map<String, String> payload = Map.of("sdp", sdp, "type", type);
        return buildMessage(WebSocketMessageType.SCREEN_SHARE_OFFER, recipientId, payload);
    }

    public String screenShareAnswer(Long recipientId, String sdp, String type) {
        Map<String, String> payload = Map.of("sdp", sdp, "type", type);
        return buildMessage(WebSocketMessageType.SCREEN_SHARE_ANSWER, recipientId, payload);
    }

    public String screenShareStarted(Long recipientId) {
        return buildMessage(WebSocketMessageType.SCREEN_SHARE_STARTED, recipientId, null);
    }

    public String screenShareStopped(Long recipientId) {
        return buildMessage(WebSocketMessageType.SCREEN_SHARE_STOPPED, recipientId, null);
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

    public String callCreated(Long callerId, Long callId) {
        Map<String, Object> payload = Map.of("callId", callId);
        return buildMessage(WebSocketMessageType.CALL_CREATED, callerId, payload);
    }

    public String voiceParticipant(WebSocketMessageType type, ChannelVoiceParticipantPayload payload) {
        return buildMessage(type, payload.channelId(), payload);
    }
}