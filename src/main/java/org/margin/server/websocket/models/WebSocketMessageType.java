package org.margin.server.websocket.models;

public enum WebSocketMessageType {
    USER_LOGIN,
    USER_LOGOUT,

    SEND_MESSAGE,
    RECEIVE_MESSAGE,
    SEND_DIRECT_MESSAGE,

    CALL_OFFER,
    CALL_RESPONSE,
    CALL_CANDIDATE,
    CALL_CREATED,
    CALL_END
}