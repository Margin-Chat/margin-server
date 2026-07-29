package org.margin.server.websocket.processors;

import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.EditMessagePayload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EditMessageProcessor implements WebSocketMessageProcessor<EditMessagePayload> {
    private final MessageService messageService;

    public EditMessageProcessor(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SEND_EDIT_MESSAGE;
    }

    @Transactional
    @Override
    public void process(User user, WebSocketMessageIn<EditMessagePayload> message) {
        messageService.editMessage(user.getId(), message.getRecipientId(), message.getPayload().messageId(), message.getPayload().content());
    }
}
