package org.margin.server.websocket.models;

import org.margin.server.websocket.models.payloads.*;

public enum WebSocketMessageType {
    // User activity
    USER_LOGIN(null),
    USER_LOGOUT(null),

    // Message
    SEND_MESSAGE(String.class),
    RECEIVE_MESSAGE(String.class),
    SEND_DIRECT_MESSAGE(String.class),

    // Call
    CALL_OFFER(IncomingCallOfferPayload.class),
    CALL_RESPONSE(IncomingCallResponsePayload.class),
    CALL_CANDIDATE(IncomingCallCandidatePayload.class),
    CALL_CREATED(null),
    CALL_END(IncomingCallEndPayload.class);

    public final Class<?> payloadClass;

    WebSocketMessageType(Class<?> payloadClass) {
        this.payloadClass = payloadClass;
    }
}