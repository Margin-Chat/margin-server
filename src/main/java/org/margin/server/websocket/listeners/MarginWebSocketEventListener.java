package org.margin.server.websocket.listeners;

import org.margin.server.social.margin.events.UserInvitedToMarginEvent;
import org.margin.server.social.space.events.UserJoinedSpaceEvent;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MarginWebSocketEventListener {

    private final WebSocketMessageBuilder messageBuilder;
    private final ConnectionManager connectionManager;
    private final UserService userService;

    public MarginWebSocketEventListener(WebSocketMessageBuilder messageBuilder,
                                        ConnectionManager connectionManager,
                                        UserService userService) {
        this.messageBuilder = messageBuilder;
        this.connectionManager = connectionManager;
        this.userService = userService;
    }

    @EventListener
    public void onMarginInvite(UserInvitedToMarginEvent event) {
        String json = messageBuilder.buildMessage(WebSocketMessageType.MARGIN_INVITE,
                event.getInvitedUserId(), event.getInviteCode());
        connectionManager.sendToUser(event.getInvitedUserId(), json);
    }

    @EventListener
    public void onUserJoinedSpace(UserJoinedSpaceEvent event) {
        Map<String, Object> payload = Map.of("spaceId", event.getSpaceId(), "user", userService.toDTO(userService.getById(event.getNewMemberId())));
        String json = messageBuilder.buildMessage(WebSocketMessageType.USER_JOINED_SPACE, event.getSpaceId(), payload);
        for (Long memberId : event.getSpaceMemberIds()) {
            if (connectionManager.isUserOnline(memberId)) {
                connectionManager.sendToUser(memberId, json);
            }
        }
    }
}