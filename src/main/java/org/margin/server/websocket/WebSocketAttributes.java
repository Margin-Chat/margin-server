package org.margin.server.websocket;

import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.util.AttributeKey;
import org.margin.server.meetings.security.MeetingGuestPrincipal;
import org.margin.server.shared.security.AuthenticatedUser;

public class WebSocketAttributes {
    private WebSocketAttributes() {
    }

    public static final AttributeKey<AuthenticatedUser> USER = AttributeKey.valueOf("user");
    public static final AttributeKey<WebSocketServerHandshaker> HANDSHAKER =
            AttributeKey.valueOf("handshaker");
    public static final AttributeKey<MeetingGuestPrincipal> MEETING_GUEST =
            AttributeKey.valueOf("meetingGuest");
}