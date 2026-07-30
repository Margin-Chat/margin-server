package org.margin.server.websocket.processors;

import org.margin.server.social.calls.services.CallService;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.IncomingCallResponsePayload;
import org.springframework.stereotype.Component;

@Component
public class CallResponseProcessor implements WebSocketMessageProcessor<IncomingCallResponsePayload> {

    private final CallService callService;

    public CallResponseProcessor(CallService callService) {
        this.callService = callService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_RESPONSE;
    }

    @Override
    public void process(AuthenticatedUser user, WebSocketMessageIn<IncomingCallResponsePayload> message) {
        IncomingCallResponsePayload payload = message.getPayload();
        callService.acceptCall(payload.callId(), payload.callerId(), message.getRecipientId(), payload.response());
    }
}