package org.margin.server.websocket.processors;

import org.margin.server.notifications.NotificationService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.IncomingCallOfferPayload;
import org.springframework.stereotype.Component;

@Component
public class ScreenShareOfferProcessor implements WebSocketMessageProcessor<IncomingCallOfferPayload> {

    private final NotificationService notificationService;

    public ScreenShareOfferProcessor(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.SCREEN_SHARE_OFFER;
    }

    @Override
    public void process(User user, WebSocketMessageIn<IncomingCallOfferPayload> message) {
        IncomingCallOfferPayload payload = message.getPayload();
        notificationService.notifyScreenShareOffer(message.getRecipientId(), payload.sdp(), payload.type());
    }
}