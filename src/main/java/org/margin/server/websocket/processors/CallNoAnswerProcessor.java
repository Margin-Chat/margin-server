package org.margin.server.websocket.processors;

import org.margin.server.social.calls.services.CallService;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;

@Component
public class CallNoAnswerProcessor implements WebSocketMessageProcessor<String> {
    private final CallService callService;

    public CallNoAnswerProcessor(CallService callService) {
        this.callService = callService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_NO_ANSWER;
    }

    @Override
    public void process(AuthenticatedUser user, WebSocketMessageIn<String> message) {
        callService.callNoAnswer(Long.valueOf(message.getPayload()), user.id(), message.getRecipientId());
    }
}
