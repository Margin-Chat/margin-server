package org.margin.server.websocket.models;

import org.margin.server.sfu.models.ChannelVoiceParticipantPayload;
import org.margin.server.subscriptions.models.dtos.SubscriptionDTO;
import org.margin.server.websocket.models.payloads.*;

public enum WebSocketMessageType {
    // User activity
    USER_LOGIN(null),
    USER_LOGOUT(null),

    // Message
    SEND_MESSAGE(SendMessagePayload.class),
    RECEIVE_MESSAGE(String.class),
    SEND_EDIT_MESSAGE(EditMessagePayload.class),
    RECEIVE_EDIT_MESSAGE(String.class),
    SEND_DELETE_MESSAGE(String.class),
    RECEIVE_DELETE_MESSAGE(String.class),
    SEND_ADD_REACTION(MessageReactionPayload.class),
    RECEIVE_ADD_REACTION(String.class),
    SEND_REMOVE_REACTION(MessageReactionPayload.class),
    RECEIVE_REMOVE_REACTION(String.class),
    CONVERSATION_READ(ConversationReadPayload.class),
    CONVERSATION_INVITE_ACCEPTED(ConversationAcceptedPayload.class),
    CONVERSATION_INVITE_DECLINED(ConversationDeclinedPayload.class),
    SEND_TYPING_INDICATOR(TypingIndicatorPayload.class),
    RECEIVE_TYPING_INDICATOR(String.class),

    // Call
    CALL_OFFER(IncomingCallOfferPayload.class),
    CALL_RESPONSE(IncomingCallResponsePayload.class),
    CALL_CANDIDATE(IncomingCallCandidatePayload.class),
    CALL_CREATED(null),
    CALL_END(IncomingCallEndPayload.class),
    CALL_NO_ANSWER(String.class),
    CALL_REJECTED(String.class),
    USER_JOINED_VOICE(ChannelVoiceParticipantPayload.class),
    USER_LEFT_VOICE(ChannelVoiceParticipantPayload.class),
    CALL_MEDIA_STATE(CallMediaStatePayload.class),

    // Notification
    NOTIFICATION(NotificationPayload.class),

    // Margin
    MARGIN_INVITE(String.class),
    USER_JOINED_SPACE(String.class),

    // Subscription
    SUBSCRIPTION_UPDATED(SubscriptionDTO.class),

    // Conversation invites
    CONVERSATION_INVITE(ConversationInvitePayload.class),

    PING(null),
    PONG(null);

    public final Class<?> payloadClass;

    WebSocketMessageType(Class<?> payloadClass) {
        this.payloadClass = payloadClass;
    }
}