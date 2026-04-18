package org.margin.server.websocket.processors;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.conversation.services.ConversationValidationService;
import org.margin.server.social.messages.models.dtos.MessageReactionDTO;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.MessageReactionPayload;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AddReactionProcessor implements WebSocketMessageProcessor<MessageReactionPayload> {
    private final ConversationService conversationService;
    private final ConversationValidationService conversationValidationService;
    private final MessageService messageService;
    private final WebSocketDeliveryService webSocketDeliveryService;

    public AddReactionProcessor(ConversationService conversationService,
                                ConversationValidationService conversationValidationService,
                                MessageService messageService,
                                WebSocketDeliveryService webSocketDeliveryService) {
        this.conversationService = conversationService;
        this.conversationValidationService = conversationValidationService;
        this.messageService = messageService;
        this.webSocketDeliveryService = webSocketDeliveryService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SEND_ADD_REACTION;
    }

    @Override
    public void process(User user, WebSocketMessageIn<MessageReactionPayload> message) {
        Conversation conversation = conversationService.getById(message.getRecipientId());
        conversationValidationService.validateUserIsInConversation(user, conversation);

        MessageReactionDTO reaction = messageService.addReaction(
                user,
                message.getPayload().messageId(),
                message.getPayload().emoji(),
                conversation
        );

        List<User> recipients = conversationService.getConversationMembers(conversation.getId());
        webSocketDeliveryService.notifyReactionAdded(reaction, recipients, conversation.getType());
    }
}
