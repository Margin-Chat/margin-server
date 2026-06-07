package org.margin.server.websocket.processors;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.calls.events.CallCandidateForwardedEvent;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.IncomingCallCandidatePayload;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CallCandidateProcessor implements WebSocketMessageProcessor<IncomingCallCandidatePayload> {

    private final ApplicationEventPublisher eventPublisher;

    public CallCandidateProcessor(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_CANDIDATE;
    }

    @Override
    public void process(User user, WebSocketMessageIn<IncomingCallCandidatePayload> message) {
        eventPublisher.publishEvent(new CallCandidateForwardedEvent(
                message.getRecipientId(),
                message.getPayload()
        ));
    }
}