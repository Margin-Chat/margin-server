package org.margin.server.websocket.processors;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.notifications.NotificationService;
import org.margin.server.social.calls.services.CallService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.IncomingCallEndPayload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CallEndProcessor implements WebSocketMessageProcessor<IncomingCallEndPayload> {

    private final CallService callService;
    private final NotificationService notificationService;

    public CallEndProcessor(CallService callService,
                            NotificationService notificationService) {
        this.callService = callService;
        this.notificationService = notificationService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_END;
    }

    @Override
    public void process(User user, WebSocketMessageIn<IncomingCallEndPayload> message) {
        IncomingCallEndPayload payload = message.getPayload();

        callService.endCall(payload.callId(), payload.callDuration());

        notificationService.notifyCallEnd(message.getRecipientId());

        log.debug("Call ended: {}", payload.callId());
    }
}