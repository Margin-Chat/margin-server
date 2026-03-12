package org.margin.server.websocket.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.notifications.Notification;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.sfu.models.ChannelVoiceParticipantPayload;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.CallResponsePayload;
import org.margin.server.websocket.models.payloads.CallSessionDescription;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class WebSocketDeliveryService {

    private final ConnectionManager connectionManager;
    private final WebSocketMessageBuilder messageBuilder;
    private final SpaceMemberRepository spaceMemberRepository;

    public WebSocketDeliveryService(ConnectionManager connectionManager,
                                    WebSocketMessageBuilder messageBuilder,
                                    SpaceMemberRepository spaceMemberRepository) {
        this.connectionManager = connectionManager;
        this.messageBuilder = messageBuilder;
        this.spaceMemberRepository = spaceMemberRepository;
    }

    public void notifyNotification(Notification notification) {
        if (connectionManager.isUserOnline(notification.getRecipient().getId())) {
            String json = messageBuilder.notification(notification);
            connectionManager.sendToUser(notification.getRecipient().getId(), json);
        }
    }

    public void notifyMessage(MessageDTO message, List<User> recipients, ConversationType conversationType) {
        String json = messageBuilder.message(message);
        sendMessageToUsers(recipients, conversationType, json);
    }

    public void notifyEditedMessage(MessageDTO message, List<User> recipients, ConversationType conversationType) {
        String json = messageBuilder.editMessage(message);
        sendMessageToUsers(recipients, conversationType, json);
    }
    public void notifyDeletedMessage(MessageResult message) {
        String json = messageBuilder.deleteMessage(message.message());
        sendMessageToUsers(message.recipients(), message.message().conversationType(), json);
    }

    public void notifyUserOnline(User user) {
        String json = messageBuilder.userActivity(WebSocketMessageType.USER_LOGIN, user);
        connectionManager.broadcast(json, user.getId());
    }

    public void notifyUserOffline(User user) {
        String json = messageBuilder.userActivity(WebSocketMessageType.USER_LOGOUT, user);
        connectionManager.broadcast(json, user.getId());
    }

    public void notifyCallOffer(Long recipientId, Long callId, UserDTO caller, String sdp, String callType) {
        String json = messageBuilder.callOffer(callId, caller, sdp, callType);
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

    public void notifySpaceMembersByChannelId(Long channelId, WebSocketMessageType type, ChannelVoiceParticipantPayload payload) {
        String json = messageBuilder.voiceParticipant(type, payload);

        List<User> members = spaceMemberRepository.findSpaceMemberByChannel_Id(channelId);

        for (User member : members) {
            if (connectionManager.isUserOnline(member.getId())) {
                connectionManager.sendToUser(member.getId(), json);
            }
        }
    }

    private void sendMessageToUsers(List<User> recipients, ConversationType conversationType, String json) {
        for (User recipient : recipients) {
            boolean isOnline = connectionManager.isUserOnline(recipient.getId());

            if (isOnline) {
                connectionManager.sendToUser(recipient.getId(), json);
            } else {
                if (conversationType == ConversationType.DIRECT ||
                        conversationType == ConversationType.GROUP) {
                    log.debug("User {} offline, message stored for later", recipient.getId());
                }
            }
        }
    }
}