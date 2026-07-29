package org.margin.server.websocket.processors;

import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.conversation.services.ConversationValidationService;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.TypingIndicatorPayload;
import org.springframework.stereotype.Component;

@Component
public class TypingIndicatorProcessor implements WebSocketMessageProcessor<TypingIndicatorPayload> {

    private final ConversationService conversationService;
    private final ConversationValidationService conversationValidationService;

    public TypingIndicatorProcessor(ConversationService conversationService,
                                    ConversationValidationService conversationValidationService) {
        this.conversationService = conversationService;
        this.conversationValidationService = conversationValidationService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SEND_TYPING_INDICATOR;
    }

    @Override
    public void process(AuthenticatedUser user, WebSocketMessageIn<TypingIndicatorPayload> message) {
        conversationValidationService.validateUserIsInConversation(user.id(), message.getRecipientId());
        conversationService.notifyTyping(user.id(), message.getRecipientId(), message.getPayload().isTyping());
    }
}
