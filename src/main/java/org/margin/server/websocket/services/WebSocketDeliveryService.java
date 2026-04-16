package org.margin.server.websocket.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.notifications.Notification;
import org.margin.server.sfu.models.ChannelVoiceParticipantPayload;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.social.conversation.models.dtos.DirectConversationDTO;
import org.margin.server.websocket.models.payloads.*;
import org.margin.server.websocket.models.payloads.ConversationInvitePayload;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WebSocketDeliveryService {

    private final ConnectionManager connectionManager;
    private final WebSocketMessageBuilder messageBuilder;
    private final SpaceMemberRepository spaceMemberRepository;
    private final UserService userService;

    public WebSocketDeliveryService(ConnectionManager connectionManager,
                                    WebSocketMessageBuilder messageBuilder,
                                    SpaceMemberRepository spaceMemberRepository, UserService userService) {
        this.connectionManager = connectionManager;
        this.messageBuilder = messageBuilder;
        this.spaceMemberRepository = spaceMemberRepository;
        this.userService = userService;
    }

    public void notifyNotification(Notification notification) {
        if (connectionManager.isUserOnline(notification.getRecipient().getId())) {
            String json = messageBuilder.notification(notification);
            connectionManager.sendToUser(notification.getRecipient().getId(), json);
        }
    }

    public void notifyMessage(MessageDTO message, List<User> recipients, ConversationType conversationType) {
        String json = messageBuilder.buildMessage(
                WebSocketMessageType.RECEIVE_MESSAGE,
                message.conversationId(),
                message
        );
        sendMessageToUsers(recipients, conversationType, json);
    }

    public void notifyEditedMessage(MessageDTO message, List<User> recipients, ConversationType conversationType) {
        String json = messageBuilder.buildMessage(
                WebSocketMessageType.RECEIVE_EDIT_MESSAGE,
                message.conversationId(),
                message
        );
        sendMessageToUsers(recipients, conversationType, json);
    }

    public void notifyDeletedMessage(MessageResult messageResult) {
        String json = messageBuilder.buildMessage(
                WebSocketMessageType.RECEIVE_DELETE_MESSAGE,
                messageResult.message().conversationId(),
                messageResult.message()
        );
        sendMessageToUsers(messageResult.recipients(), messageResult.message().conversationType(), json);
    }

    public void notifyUserOnline(User user) {
        String json = messageBuilder.buildMessage(WebSocketMessageType.USER_LOGIN, user.getId(), new UserDTO(
                user,
                connectionManager.isUserOnline(user.getId()))
        );
        connectionManager.broadcast(json, user.getId());
    }

    public void notifyUserOffline(User user) {
        String json = messageBuilder.buildMessage(
                WebSocketMessageType.USER_LOGOUT,
                user.getId(),
                new UserDTO(user, connectionManager.isUserOnline(user.getId()))
        );
        connectionManager.broadcast(json, user.getId());
    }

    public void notifyCallOffer(Long recipientId, Long callId, UserDTO caller, String sdp, String callType) {
        CallOfferPayload payload = new CallOfferPayload(callId, caller, sdp, callType);
        String json = messageBuilder.buildMessage(WebSocketMessageType.CALL_OFFER, caller.id(), payload);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyCallResponse(Long recipientId, Long callId, Long callerId, CallSessionDescription response) {
        var payload = new CallResponsePayload(callId, callerId, response);
        String json = messageBuilder.buildMessage(WebSocketMessageType.CALL_RESPONSE, recipientId, payload);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyCallCandidate(Long recipientId, IncomingCallCandidatePayload payload) {
        String json = messageBuilder.buildMessage(WebSocketMessageType.CALL_CANDIDATE, recipientId, payload);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyCallEnd(Long recipientId) {
        String json = messageBuilder.buildMessage(WebSocketMessageType.CALL_END, recipientId, null);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyCallCreated(Long callerId, Long callId) {
        Map<String, Object> payload = Map.of("callId", callId);
        String json = messageBuilder.buildMessage(WebSocketMessageType.CALL_CREATED, callerId, payload);
        connectionManager.sendToUser(callerId, json);
    }

    public void notifyCallNoAnswer(User recepientUser) {
        String json = messageBuilder.buildMessage(WebSocketMessageType.CALL_NO_ANSWER, recepientUser.getId(), null);
        connectionManager.sendToUser(recepientUser.getId(), json);
    }

    public void notifyCallMediaState(Long recipientId, CallMediaStatePayload payload) {
        String json = messageBuilder.buildMessage(WebSocketMessageType.CALL_MEDIA_STATE, recipientId, payload);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifySpaceMembersByChannelId(Long channelId,
                                              WebSocketMessageType type,
                                              ChannelVoiceParticipantPayload payload) {
        String json = messageBuilder.buildMessage(type, payload.channelId(), payload);
        List<User> members = spaceMemberRepository.findSpaceMemberByChannel_Id(channelId);

        for (User member : members) {
            if (connectionManager.isUserOnline(member.getId())) {
                connectionManager.sendToUser(member.getId(), json);
            }
        }
    }

    public void notifyMarginInvite(String inviteCode,
                                   Long recipientId) {
        String json = messageBuilder.buildMessage(WebSocketMessageType.MARGIN_INVITE, recipientId, inviteCode);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyConversationInvite(DirectConversationDTO conversation, User sender, Long recipientId) {
        ConversationInvitePayload payload = new ConversationInvitePayload(conversation, userService.toDTO(sender));
        String json = messageBuilder.buildMessage(WebSocketMessageType.CONVERSATION_INVITE, recipientId, payload);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyConversationRead(Long conversationId, Long recipientId, Instant readAt) {
        ConversationReadPayload payload = new ConversationReadPayload(conversationId, readAt);
        String json = messageBuilder.buildMessage(WebSocketMessageType.CONVERSATION_READ, recipientId, payload);
        connectionManager.sendToUser(recipientId, json);
    }

    public void notifyUserJoinedSpace(List<User> spaceMembers, User newMember, Long spaceId) {
        Map<String, Object> payload = Map.of("spaceId", spaceId, "user", userService.toDTO(newMember));
        String json = messageBuilder.buildMessage(WebSocketMessageType.USER_JOINED_SPACE, spaceId, payload);
        for (User member : spaceMembers) {
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