package org.margin.server.websocket.processors;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.conversation.services.ConversationValidationService;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SendMessageProcessor implements WebSocketMessageProcessor<String> {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final ConversationValidationService conversationValidationService;

    public SendMessageProcessor(ConversationService conversationService,
                                MessageService messageService, ConversationValidationService conversationValidationService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.conversationValidationService = conversationValidationService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SEND_MESSAGE;
    }

    @Transactional
    @Override
    public void process(User user, WebSocketMessageIn<String> message) {
        Conversation conversation = conversationService.getById(message.getRecipientId());
        conversationValidationService.validateUserIsInConversation(user, conversation);
        messageService.sendMessage(user, message.getPayload(), conversation);
    }
}