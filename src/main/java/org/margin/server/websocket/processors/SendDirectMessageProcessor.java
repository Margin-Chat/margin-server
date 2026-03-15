package org.margin.server.websocket.processors;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.conversation.services.ConversationValidationService;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SendDirectMessageProcessor implements WebSocketMessageProcessor<String> {

    private final MessageService messageService;
    private final WebSocketDeliveryService webSocketDeliveryService;
    private final UserService userService;
    private final ConversationService conversationService;
    private final ConversationValidationService conversationValidationService;
    private final ConversationRepository conversationRepository;

    public SendDirectMessageProcessor(MessageService messageService,
                                      WebSocketDeliveryService webSocketDeliveryService,
                                      UserService userService, ConversationService conversationService, ConversationValidationService conversationValidationService, ConversationRepository conversationRepository) {
        this.messageService = messageService;
        this.webSocketDeliveryService = webSocketDeliveryService;
        this.userService = userService;
        this.conversationService = conversationService;
        this.conversationValidationService = conversationValidationService;
        this.conversationRepository = conversationRepository;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SEND_DIRECT_MESSAGE;
    }

    @Override
    public void process(User user, WebSocketMessageIn<String> message) {
        User toUser = userService.getById(message.getRecipientId());
        Optional<Conversation> conversation = conversationRepository.findById(message.getRecipientId());
        conversation.ifPresent(c -> conversationValidationService.validateUserIsInConversation(user, c));

        MessageResult result = messageService.sendDirectMessage(
                user,
                toUser,
                message.getPayload()
        );

        webSocketDeliveryService.notifyMessage(
                result.message(),
                result.recipients(),
                ConversationType.DIRECT
        );
    }
}