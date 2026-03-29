package org.margin.server.websocket.processors;

import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.social.calls.models.CallType;
import org.margin.server.social.calls.services.CallService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.IncomingCallOfferPayload;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.stereotype.Component;

@Component
public class CallOfferProcessor implements WebSocketMessageProcessor<IncomingCallOfferPayload> {

    private final CallService callService;
    private final WebSocketDeliveryService webSocketDeliveryService;
    private final UserService userService;

    public CallOfferProcessor(CallService callService,
                              WebSocketDeliveryService webSocketDeliveryService,
                              UserService userService) {
        this.callService = callService;
        this.webSocketDeliveryService = webSocketDeliveryService;
        this.userService = userService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_OFFER;
    }

    @Override
    public void process(User user, WebSocketMessageIn<IncomingCallOfferPayload> message) {
        IncomingCallOfferPayload payload = message.getPayload();

        Call call = callService.createCall(
                user,
                userService.getById(message.getRecipientId()),
                CallStatus.OFFERED,
                CallType.AUDIO
        );

        webSocketDeliveryService.notifyCallOffer(
                message.getRecipientId(),
                call.getId(),
                userService.toDTO(user),
                payload.sdp(),
                CallType.AUDIO.toString()
        );

        webSocketDeliveryService.notifyCallCreated(
                user.getId(),
                call.getId()
        );
    }
}