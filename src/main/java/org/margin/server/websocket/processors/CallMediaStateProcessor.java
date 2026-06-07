package org.margin.server.websocket.processors;

import org.margin.server.social.calls.events.CallMediaStateEvent;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.CallMediaStatePayload;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class CallMediaStateProcessor implements WebSocketMessageProcessor<CallMediaStatePayload> {

    private final ApplicationEventPublisher eventPublisher;

    public CallMediaStateProcessor(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_MEDIA_STATE;
    }

    @Override
    public void process(User user, WebSocketMessageIn<CallMediaStatePayload> message) {
        eventPublisher.publishEvent(new CallMediaStateEvent(message.getRecipientId(), message.getPayload()));
    }
}