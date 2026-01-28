package org.margin.server.websocket.handlers;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.notifications.NotificationService;
import org.margin.server.social.communication.messages.models.ChannelMessageResult;
import org.margin.server.social.communication.messages.models.dtos.DirectMessageDTO;
import org.margin.server.social.communication.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageHandler {

    private final MessageService messageService;
    private final NotificationService notificationService;

    public MessageHandler(MessageService messageService,
                          NotificationService notificationService) {
        this.messageService = messageService;
        this.notificationService = notificationService;
    }

    public void handleDirectMessage(User fromUser, WebSocketMessageIn<String> wsMessage) {
        DirectMessageDTO message = messageService.sendDirectMessage(
                fromUser.getId(),
                wsMessage.getRecipientId(),
                wsMessage.getPayload()
        );

        notificationService.notifyDirectMessage(message);
    }

    public void handleChannelMessage(User fromUser, WebSocketMessageIn<String> wsMessage) {
        ChannelMessageResult channelMessageResult = messageService.sendChannelMessage(
                fromUser.getId(),
                wsMessage.getRecipientId(),
                wsMessage.getPayload()
        );

        notificationService.notifyChannelMessage(
                channelMessageResult.message(),
                channelMessageResult.recipients()
        );
    }
}