package org.margin.server.websocket.processors;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.conversation.services.ConversationValidationService;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class DeleteMessageProcessor implements WebSocketMessageProcessor<String> {
    private final ConversationService conversationService;
    private final ConversationValidationService conversationValidationService;
    private final MessageService messageService;
    private final WebSocketDeliveryService webSocketDeliveryService;

    public DeleteMessageProcessor(ConversationService conversationService, ConversationValidationService conversationValidationService, MessageService messageService, WebSocketDeliveryService webSocketDeliveryService) {
        this.conversationService = conversationService;
        this.conversationValidationService = conversationValidationService;
        this.messageService = messageService;
        this.webSocketDeliveryService = webSocketDeliveryService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SEND_DELETE_MESSAGE;
    }

    @Override
    @Transactional
    public void process(User user, WebSocketMessageIn<String> message) {
        Conversation conversation = conversationService.getById(message.getRecipientId());
        conversationValidationService.validateUserIsInConversation(user, conversation);

        MessageResult result = messageService.deleteMessage(
                Long.parseLong(message.getPayload()), conversation);

        webSocketDeliveryService.notifyDeletedMessage(result);
    }
}
