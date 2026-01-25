package org.margin.server.websocket.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.communication.messages.models.SpaceChannelMessage;
import org.margin.server.users.models.UserDTO;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.CallOfferPayload;
import org.margin.server.websocket.models.payloads.CallResponsePayload;
import org.margin.server.websocket.models.payloads.IncomingCallCandidatePayload;
import org.springframework.stereotype.Component;
import org.margin.server.social.communication.messages.models.DirectMessage;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessage;

@Component
@Slf4j
public class WebSocketMessageBuilder {
    private final ObjectMapper mapper;

    public WebSocketMessageBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String createUserActivity(WebSocketMessageType type, User user) {
        WebSocketMessage<UserDTO> message = new WebSocketMessage<>(
                type,
                System.currentTimeMillis(),
                user.getId(),
                new UserDTO(user));
        return toJson(message);
    }

    public String createWebSocketChatMessage(DirectMessage directMessage) {
        WebSocketMessage<String> message = new WebSocketMessage<>(
                WebSocketMessageType.RECEIVE_DIRECT_MESSAGE,
                System.currentTimeMillis(),
                directMessage.getToUserId(),
                toJson(directMessage));
        return toJson(message);
    }

    public String createWebSocketChannelMessage(SpaceChannelMessage channelMessage) {
        WebSocketMessage<String> message = new WebSocketMessage<>(
                WebSocketMessageType.RECEIVE_CHANNEL_MESSAGE,
                System.currentTimeMillis(),
                channelMessage.getChannelId(),
                toJson(channelMessage));
        return toJson(message);
    }

    public String createWebSocketCallResponseMessageWithPayload(
            Long toUserId,
            CallResponsePayload payload) {
        WebSocketMessage<CallResponsePayload> message = new WebSocketMessage<>(
                WebSocketMessageType.CALL_RESPONSE,
                System.currentTimeMillis(),
                toUserId,
                payload
        );
        return toJson(message);
    }

    public String createWebSocketCallCandidateMessage(
            Long toUserId,
            IncomingCallCandidatePayload payload
    ) {
        WebSocketMessage<IncomingCallCandidatePayload> message = new WebSocketMessage<>(
                WebSocketMessageType.CALL_CANDIDATE,
                System.currentTimeMillis(),
                toUserId,
                payload
        );
        return toJson(message);
    }

    public String createWebSocketCallOfferMessage(Long callId,
                                                  Long toUserId,
                                                  String sdp,
                                                  String callType) {
        CallOfferPayload offerData = new CallOfferPayload(
                callId,
                toUserId,
                sdp,
                callType
        );

        WebSocketMessage<CallOfferPayload> message = new WebSocketMessage<>(
                WebSocketMessageType.CALL_OFFER,
                System.currentTimeMillis(),
                toUserId,
                offerData);
        return toJson(message);
    }

    public String createWebSocketCallEndMessage(Long toUserId) {
        WebSocketMessage<Void> message = new WebSocketMessage<>(
                WebSocketMessageType.CALL_END,
                System.currentTimeMillis(),
                toUserId,
                null
        );
        return toJson(message);
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON: {}", obj.getClass().getName(), e);
            throw new RuntimeException("Failed to serialize WebSocket message", e);
        }
    }
}
