package org.margin.server.websocket.models;

import org.margin.server.sfu.models.ChannelVoiceParticipantPayload;
import org.margin.server.websocket.models.payloads.*;

public enum WebSocketMessageType {
    // User activity
    USER_LOGIN(null),
    USER_LOGOUT(null),

    // Message
    SEND_MESSAGE(String.class),
    RECEIVE_MESSAGE(String.class),
    SEND_EDIT_MESSAGE(EditMessagePayload.class),
    RECEIVE_EDIT_MESSAGE(String.class),
    SEND_DELETE_MESSAGE(String.class),
    RECEIVE_DELETE_MESSAGE(String.class),

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
    USER_JOINED_SPACE(String.class);

    public final Class<?> payloadClass;

    WebSocketMessageType(Class<?> payloadClass) {
        this.payloadClass = payloadClass;
    }
}