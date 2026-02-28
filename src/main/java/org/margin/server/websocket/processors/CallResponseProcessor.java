package org.margin.server.websocket.processors;

import org.margin.server.notifications.NotificationService;
import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.social.calls.services.CallService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.IncomingCallResponsePayload;
import org.springframework.stereotype.Component;

@Component
public class CallResponseProcessor implements WebSocketMessageProcessor<IncomingCallResponsePayload> {

    private final CallService callService;
    private final NotificationService notificationService;

    public CallResponseProcessor(CallService callService,
                                 NotificationService notificationService) {
        this.callService = callService;
        this.notificationService = notificationService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_RESPONSE;
    }

    @Override
    public void process(User user, WebSocketMessageIn<IncomingCallResponsePayload> message) {
        IncomingCallResponsePayload payload = message.getPayload();

        callService.updateCallStatus(payload.callId(), CallStatus.ACCEPTED);

        notificationService.notifyCallResponse(
                message.getRecipientId(),
                payload.callId(),
                payload.callerId(),
                payload.response()
        );
    }
}