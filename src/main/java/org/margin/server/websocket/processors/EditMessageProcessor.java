package org.margin.server.websocket.processors;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.EditMessagePayload;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.stereotype.Component;

@Component
public class EditMessageProcessor implements WebSocketMessageProcessor<EditMessagePayload> {
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final WebSocketDeliveryService webSocketDeliveryService;

    public EditMessageProcessor(ConversationService conversationService,
                                MessageService messageService,
                                WebSocketDeliveryService webSocketDeliveryService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.webSocketDeliveryService = webSocketDeliveryService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SEND_EDIT_MESSAGE;
    }

    @Override
    public void process(User user, WebSocketMessageIn<EditMessagePayload> message) {
        Conversation conversation = conversationService.getById(message.getRecipientId());

        MessageResult result = messageService.editMessage(
                conversation,
                message.getPayload().messageId(),
                message.getPayload().content());

        webSocketDeliveryService.notifyEditedMessage(result.message(), result.recipients(), conversation.getType());
    }
}
