package org.margin.server.websocket.processors;

import org.margin.server.notifications.NotificationService;
import org.margin.server.social.conversation.Conversation;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.messages.models.dtos.MessageResult;
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
    private final NotificationService notificationService;

    public SendMessageProcessor(ConversationService conversationService,
                                MessageService messageService,
                                NotificationService notificationService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.notificationService = notificationService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SEND_MESSAGE;
    }

    @Transactional
    @Override
    public void process(User user, WebSocketMessageIn<String> message) {
        Conversation conversation = conversationService.getById(message.getRecipientId());
        MessageResult result = messageService.sendMessage(user, conversation, message.getPayload());
        notificationService.notifyMessage(result.message(), result.recipients(), conversation.getType());
    }
}