package org.margin.server.websocket.listeners;

import org.margin.server.social.conversation.events.ConversationInviteAcceptedEvent;
import org.margin.server.social.conversation.events.ConversationInviteEvent;
import org.margin.server.social.conversation.events.ConversationReadEvent;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.ConversationAcceptedPayload;
import org.margin.server.websocket.models.payloads.ConversationInvitePayload;
import org.margin.server.websocket.models.payloads.ConversationReadPayload;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ConversationWebSocketEventListener {

    private final WebSocketMessageBuilder messageBuilder;
    private final ConnectionManager connectionManager;
    private final UserService userService;

    public ConversationWebSocketEventListener(WebSocketMessageBuilder messageBuilder,
                                              ConnectionManager connectionManager,
                                              UserService userService) {
        this.messageBuilder = messageBuilder;
        this.connectionManager = connectionManager;
        this.userService = userService;
    }

    @EventListener
    public void onConversationRead(ConversationReadEvent event) {
        ConversationReadPayload payload = new ConversationReadPayload(event.getConversationId(), event.getReadAt());
        String json = messageBuilder.buildMessage(WebSocketMessageType.CONVERSATION_READ, event.getRecipientId(), payload);
        connectionManager.sendToUser(event.getRecipientId(), json);
    }

    @EventListener
    public void onConversationInvite(ConversationInviteEvent event) {
        ConversationInvitePayload payload = new ConversationInvitePayload(
                event.getConversation(), userService.toDTO(event.getSender()));
        String json = messageBuilder.buildMessage(WebSocketMessageType.CONVERSATION_INVITE, event.getRecipientId(), payload);
        connectionManager.sendToUser(event.getRecipientId(), json);
    }

    @EventListener
    public void onConversationInviteAccepted(ConversationInviteAcceptedEvent event) {
        ConversationAcceptedPayload payload = new ConversationAcceptedPayload(event.getConversation(), event.getAcceptedBy());
        String json = messageBuilder.buildMessage(WebSocketMessageType.CONVERSATION_INVITE_ACCEPTED, event.getRecipientId(), payload);
        connectionManager.sendToUser(event.getRecipientId(), json);
    }
}