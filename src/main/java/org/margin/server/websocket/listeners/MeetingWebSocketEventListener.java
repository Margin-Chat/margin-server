package org.margin.server.websocket.listeners;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.meetings.api.MeetingLobbyRegistry;
import org.margin.server.meetings.events.MeetingAdmittedEvent;
import org.margin.server.meetings.events.MeetingDeniedEvent;
import org.margin.server.meetings.events.MeetingKnockEvent;
import org.margin.server.meetings.events.MeetingParticipantRemovedEvent;
import org.margin.server.meetings.events.MeetingRingEvent;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class MeetingWebSocketEventListener {

    private final WebSocketMessageBuilder messageBuilder;
    private final ConnectionManager connectionManager;
    private final MeetingLobbyRegistry lobbyRegistry;

    public MeetingWebSocketEventListener(WebSocketMessageBuilder messageBuilder,
                                         ConnectionManager connectionManager,
                                         MeetingLobbyRegistry lobbyRegistry) {
        this.messageBuilder = messageBuilder;
        this.connectionManager = connectionManager;
        this.lobbyRegistry = lobbyRegistry;
    }

    @EventListener
    public void onKnock(MeetingKnockEvent event) {
        for (Long hostId : event.hostUserIds()) {
            if (!connectionManager.isUserOnline(hostId)) {
                continue;
            }
            String json = messageBuilder.buildMessage(
                    WebSocketMessageType.MEETING_KNOCK_RECEIVED,
                    hostId,
                    Map.of("meetingId", event.meetingId(),
                            "guestUserId", event.guestUserId(),
                            "displayName", event.displayName()));
            connectionManager.sendToUser(hostId, json);
        }
    }

    @EventListener
    public void onRing(MeetingRingEvent event) {
        String json = messageBuilder.buildMessage(
                WebSocketMessageType.MEETING_INVITE, event.recipientId(), event.payload());
        connectionManager.sendToUser(event.recipientId(), json);
    }

    @EventListener
    public void onAdmitted(MeetingAdmittedEvent event) {
        sendToLobby(event.meetingId(), event.guestUserId(), WebSocketMessageType.MEETING_ADMITTED);
    }

    @EventListener
    public void onDenied(MeetingDeniedEvent event) {
        sendToLobby(event.meetingId(), event.guestUserId(), WebSocketMessageType.MEETING_DENIED);
    }

    @EventListener
    public void onRemoved(MeetingParticipantRemovedEvent event) {
        sendToLobby(event.meetingId(), event.guestUserId(), WebSocketMessageType.MEETING_REMOVED);
    }

    private void sendToLobby(Long meetingId, Long guestUserId, WebSocketMessageType type) {
        if (guestUserId == null) {
            return;
        }
        String json = messageBuilder.buildMessage(type, guestUserId, Map.of("meetingId", meetingId));
        lobbyRegistry.send(meetingId, guestUserId, json);
    }
}
