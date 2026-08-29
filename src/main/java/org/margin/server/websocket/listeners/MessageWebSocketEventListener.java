package org.margin.server.websocket.listeners;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.api.ConversationType;
import org.margin.server.social.messages.events.*;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class MessageWebSocketEventListener {

    private final WebSocketMessageBuilder messageBuilder;
    private final ConnectionManager connectionManager;

    public MessageWebSocketEventListener(WebSocketMessageBuilder messageBuilder, ConnectionManager connectionManager) {
        this.messageBuilder = messageBuilder;
        this.connectionManager = connectionManager;
    }

    @EventListener
    public void onMessageSent(MessageSentEvent event) {
        String json = messageBuilder.buildMessage(
                WebSocketMessageType.RECEIVE_MESSAGE,
                event.getMessage().conversationId(),
                event.getMessage()
        );
        sendToUsers(event.getRecipientIds(), event.getMessage().conversationType(), json);
    }

    @EventListener
    public void onMessageEdited(MessageEditedEvent event) {
        String json = messageBuilder.buildMessage(
                WebSocketMessageType.RECEIVE_EDIT_MESSAGE,
                event.getMessage().conversationId(),
                event.getMessage()
        );
        sendToUsers(event.getRecipientIds(), event.getConversationType(), json);
    }

    @EventListener
    public void onMessageDeleted(MessageDeletedEvent event) {
        String json = messageBuilder.buildMessage(
                WebSocketMessageType.RECEIVE_DELETE_MESSAGE,
                event.getMessageResult().message().conversationId(),
                event.getMessageResult().message()
        );
        sendToUsers(event.getMessageResult().recipientIds(),
                event.getMessageResult().message().conversationType(), json);
    }

    @EventListener
    public void onReactionAdded(ReactionAddedEvent event) {
        String json = messageBuilder.buildMessage(
                WebSocketMessageType.RECEIVE_ADD_REACTION,
                event.getReaction().conversationId(),
                event.getReaction()
        );
        sendToUsers(event.getRecipientIds(), event.getConversationType(), json);
    }

    @EventListener
    public void onReactionRemoved(ReactionRemovedEvent event) {
        String json = messageBuilder.buildMessage(
                WebSocketMessageType.RECEIVE_REMOVE_REACTION,
                event.getReaction().conversationId(),
                event.getReaction()
        );
        sendToUsers(event.getRecipientIds(), event.getConversationType(), json);
    }

    private void sendToUsers(List<Long> recipientIds, ConversationType conversationType, String json) {
        for (Long recipientId : recipientIds) {
            if (connectionManager.isUserOnline(recipientId)) {
                connectionManager.sendToUser(recipientId, json);
            } else if (conversationType == ConversationType.DIRECT || conversationType == ConversationType.GROUP) {
                log.debug("AuthenticatedUser {} offline, message stored for later", recipientId);
            }
        }
    }
}