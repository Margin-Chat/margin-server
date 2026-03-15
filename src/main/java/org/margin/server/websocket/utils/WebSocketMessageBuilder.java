package org.margin.server.websocket.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.notifications.Notification;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.websocket.models.WebSocketMessage;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.NotificationPayload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebSocketMessageBuilder {

    private final ObjectMapper mapper;

    public WebSocketMessageBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String message(MessageDTO message) {
        return buildMessage(
                WebSocketMessageType.RECEIVE_MESSAGE,
                message.conversationId(),
                message
        );
    }

    public String notification(Notification notification) {
        var payload = new NotificationPayload(
                notification.getNotificationId(),
                notification.getType(),
                notification.getReferenceId(),
                notification.getMarginId(),
                notification.getSender() != null ? new UserDTO(notification.getSender(), false) : null
        );
        return buildMessage(WebSocketMessageType.NOTIFICATION, notification.getRecipient().getId(), payload);
    }

    public <T> String buildMessage(WebSocketMessageType type, Long recipientId, T payload) {
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