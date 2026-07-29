package org.margin.server.websocket.processors;

import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.social.calls.models.CallType;
import org.margin.server.social.calls.services.CallService;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.IncomingCallOfferPayload;
import org.springframework.stereotype.Component;

@Component
public class CallOfferProcessor implements WebSocketMessageProcessor<IncomingCallOfferPayload> {

    private final CallService callService;

    public CallOfferProcessor(CallService callService) {
        this.callService = callService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_OFFER;
    }

    @Override
    public void process(AuthenticatedUser user, WebSocketMessageIn<IncomingCallOfferPayload> message) {
        IncomingCallOfferPayload payload = message.getPayload();

        callService.createCall(
                user.id(),
                message.getRecipientId(),
                CallStatus.OFFERED,
                CallType.AUDIO,
                payload.sdp()
        );
    }
}