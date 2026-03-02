package org.margin.server.notifications;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.connection.ConnectionManager;
import org.margin.server.social.conversation.ConversationType;
import org.margin.server.social.messages.models.dtos.MessageDTO;
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

    public void notifyMessage(MessageDTO message, List<User> recipients, ConversationType conversationType) {
        String json = messageBuilder.message(message);

        for (User recipient : recipients) {
            boolean isOnline = connectionManager.isUserOnline(recipient.getId());

            if (isOnline) {
                connectionManager.sendToUser(recipient.getId(), json);
            } else {
                // Only send push notifications for DMs and groups, not channels
                if (conversationType == ConversationType.DIRECT ||
                        conversationType == ConversationType.GROUP) {
                    log.debug("User {} offline, message stored for later", recipient.getId());
                    // TODO: Send push notification
                }
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

    public void notifyCallCreated(Long callerId, Long callId) {
        String json = messageBuilder.callCreated(callerId, callId);
        connectionManager.sendToUser(callerId, json);
    }

    public void notifyScreenShareOffer(Long recipientId, String sdp, String type) {
        String json = messageBuilder.screenShareOffer(recipientId, sdp, type);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyScreenShareAnswer(Long recipientId, String sdp, String type) {
        String json = messageBuilder.screenShareAnswer(recipientId, sdp, type);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyScreenShareStarted(Long recipientId) {
        String json = messageBuilder.screenShareStarted(recipientId);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyScreenShareStopped(Long recipientId) {
        String json = messageBuilder.screenShareStopped(recipientId);
        connectionManager.sendToUser(recipientId, json);
    }
}