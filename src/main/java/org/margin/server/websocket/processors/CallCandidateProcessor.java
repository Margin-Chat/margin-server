package org.margin.server.websocket.processors;

import org.margin.server.notifications.NotificationService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.IncomingCallCandidatePayload;
import org.springframework.stereotype.Component;

@Component
public class CallCandidateProcessor implements WebSocketMessageProcessor<IncomingCallCandidatePayload> {

    private final NotificationService notificationService;

    public CallCandidateProcessor(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_CANDIDATE;
    }

    @Override
    public void process(User user, WebSocketMessageIn<IncomingCallCandidatePayload> message) {
        notificationService.notifyCallCandidate(
                message.getRecipientId(),
                message.getPayload()
        );
    }
}