package org.margin.server.websocket.processors;

import org.margin.server.notifications.NotificationType;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.services.CallService;
import org.margin.server.social.calls.services.CallValidationService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class CallNoAnswerProcessor implements WebSocketMessageProcessor<String> {
    private final CallService callService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final WebSocketDeliveryService webSocketDeliveryService;
    private final CallValidationService callValidationService;

    public CallNoAnswerProcessor(CallService callService,
                                 NotificationService notificationService,
                                 UserService userService, WebSocketDeliveryService webSocketDeliveryService, CallValidationService callValidationService) {
        this.callService = callService;
        this.notificationService = notificationService;
        this.userService = userService;
        this.webSocketDeliveryService = webSocketDeliveryService;
        this.callValidationService = callValidationService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_NO_ANSWER;
    }

    @Override
    public void process(User user, WebSocketMessageIn<String> message) {
        Call call = callService.getById(Long.valueOf(message.getPayload()));

        callValidationService.validateUserIsSender(call, user);
        User recepientUser = userService.getById(message.getRecipientId());

        callService.callNoAnswer(call);
        notificationService.createForUsers(
                Collections.singletonList(recepientUser),
                user,
                NotificationType.MISSED_CALL,
                call.getId());
        webSocketDeliveryService.notifyCallNoAnswer(recepientUser);
    }
}
