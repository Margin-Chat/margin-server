package org.margin.server.websocket.processors;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class DeleteMessageProcessor implements WebSocketMessageProcessor<String> {
    private final MessageService messageService;

    public DeleteMessageProcessor(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SEND_DELETE_MESSAGE;
    }

    @Override
    @Transactional
    public void process(User user, WebSocketMessageIn<String> message) {
        messageService.deleteMessage(user.getId(), Long.parseLong(message.getPayload()), message.getRecipientId());
    }
}
