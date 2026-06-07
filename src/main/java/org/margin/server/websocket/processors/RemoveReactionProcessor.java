package org.margin.server.websocket.processors;

import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.MessageReactionPayload;
import org.springframework.stereotype.Component;

@Component
public class RemoveReactionProcessor implements WebSocketMessageProcessor<MessageReactionPayload> {
    private final MessageService messageService;

    public RemoveReactionProcessor(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SEND_REMOVE_REACTION;
    }

    @Override
    public void process(User user, WebSocketMessageIn<MessageReactionPayload> message) {
        messageService.removeReaction(
                user,
                message.getPayload().messageId(),
                message.getPayload().emoji(),
                message.getRecipientId()
        );
    }
}
