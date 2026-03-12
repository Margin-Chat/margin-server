package org.margin.server.websocket.processors;

import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;

@Component
public class SendDirectMessageProcessor implements WebSocketMessageProcessor<String> {

    private final MessageService messageService;
    private final WebSocketDeliveryService webSocketDeliveryService;
    private final UserService userService;

    public SendDirectMessageProcessor(MessageService messageService,
                                      WebSocketDeliveryService webSocketDeliveryService,
                                      UserService userService) {
        this.messageService = messageService;
        this.webSocketDeliveryService = webSocketDeliveryService;
        this.userService = userService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SEND_DIRECT_MESSAGE;
    }

    @Override
    public void process(User user, WebSocketMessageIn<String> message) {
        User toUser = userService.getById(message.getRecipientId());

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