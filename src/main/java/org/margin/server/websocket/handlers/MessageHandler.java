package org.margin.server.websocket.handlers;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.notifications.NotificationService;
import org.margin.server.social.conversation.Conversation;
import org.margin.server.social.conversation.ConversationType;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class MessageHandler {

    private final MessageService messageService;
    private final NotificationService notificationService;
    private final ConversationService conversationService;
    private final UserService userService;

    public MessageHandler(MessageService messageService,
                          NotificationService notificationService,
                          ConversationService conversationService,
                          UserService userService) {
        this.messageService = messageService;
        this.notificationService = notificationService;
        this.conversationService = conversationService;
        this.userService = userService;
    }

    @Transactional
    public void handleMessage(User fromUser, WebSocketMessageIn<String> wsMessage) {
        Conversation conversation = conversationService.getById(wsMessage.getRecipientId());

        MessageResult result = messageService.sendMessage(
                fromUser,
                conversation,
                wsMessage.getPayload()
        );

        notificationService.notifyMessage(
                result.message(),
                result.recipients(),
                conversation.getType()
        );
    }

    public void handleNewDirectMessage(User fromUser, WebSocketMessageIn<String> wsMessage) {
        User toUser = userService.getById(wsMessage.getRecipientId());

        MessageResult result = messageService.sendDirectMessage(
                fromUser,
                toUser,
                wsMessage.getPayload()
        );

        notificationService.notifyMessage(
                result.message(),
                result.recipients(),
                ConversationType.DIRECT
        );
    }
}