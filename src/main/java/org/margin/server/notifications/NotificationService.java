package org.margin.server.notifications;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.connection.ConnectionManager;
import org.margin.server.social.communication.messages.models.dtos.DirectMessageDTO;
import org.margin.server.social.communication.messages.models.dtos.ChannelMessageDTO;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.CallResponsePayload;
import org.margin.server.websocket.models.payloads.CallSessionDescription;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class NotificationService {

    private final ConnectionManager connectionManager;
    private final WebSocketMessageBuilder messageBuilder;

    public NotificationService(ConnectionManager connectionManager,
                               WebSocketMessageBuilder messageBuilder) {
        this.connectionManager = connectionManager;
        this.messageBuilder = messageBuilder;
    }

    public void notifyDirectMessage(DirectMessageDTO message) {
        if (!connectionManager.isUserOnline(message.toUserId())) {
            log.debug("User {} offline, message stored for later", message.toUserId());
            // TODO: Push notification
            return;
        }

        String json = messageBuilder.directMessage(message);
        connectionManager.sendToUser(message.toUserId(), json);
    }

    public void notifyChannelMessage(ChannelMessageDTO message, List<User> recipients) {
        String json = messageBuilder.channelMessage(message);

        for (User recipient : recipients) {
            if (!recipient.getId().equals(message.fromUserId())) {
                connectionManager.sendToUser(recipient.getId(), json);
            }
        }
    }

    public void notifyUserOnline(User user) {
        String json = messageBuilder.userActivity(WebSocketMessageType.USER_LOGIN, user);
        connectionManager.broadcast(json, user.getId());
    }

    public void notifyUserOffline(User user) {
        String json = messageBuilder.userActivity(WebSocketMessageType.USER_LOGOUT, user);
        connectionManager.broadcast(json, user.getId());
    }

    public void notifyCallOffer(Long recipientId, Long callId, Long callerId, String sdp, String callType) {
        String json = messageBuilder.callOffer(callId, callerId, sdp, callType);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyCallResponse(Long recipientId, Long callId, Long callerId, CallSessionDescription response) {
        var payload = new CallResponsePayload(callId, callerId, response);
        String json = messageBuilder.callResponse(recipientId, payload);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyCallCandidate(Long recipientId,
                                    org.margin.server.websocket.models.payloads.IncomingCallCandidatePayload payload) {
        String json = messageBuilder.callCandidate(recipientId, payload);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyCallEnd(Long recipientId) {
        String json = messageBuilder.callEnd(recipientId);
        connectionManager.sendToUser(recipientId, json);
    }
}