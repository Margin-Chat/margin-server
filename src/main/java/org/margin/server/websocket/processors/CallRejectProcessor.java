package org.margin.server.websocket.processors;

import org.margin.server.social.calls.services.CallService;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;

@Component
public class CallRejectProcessor implements WebSocketMessageProcessor<String> {
    private final CallService callService;

    public CallRejectProcessor(CallService callService) {
        this.callService = callService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_REJECTED;
    }

    @Override
    public void process(AuthenticatedUser user, WebSocketMessageIn<String> message) {
        callService.rejectCall(Long.valueOf(message.getPayload()), message.getRecipientId(), user.id());
    }
}
