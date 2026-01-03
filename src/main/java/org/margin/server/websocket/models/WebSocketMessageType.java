package org.margin.server.websocket.models;

public enum WebSocketMessageType {
    USER_LOGIN,
    USER_LOGOUT,
    SEND_DIRECT_MESSAGE,
    RECEIVE_DIRECT_MESSAGE,
    SEND_SPACE_MESSAGE,
    RECEIVE_SPACE_MESSAGE,
    CALL_OFFER,
    CALL_RESPONSE,
    CALL_CANDIDATE,
    CALL_END
}
