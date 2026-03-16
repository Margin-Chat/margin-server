package org.margin.server.websocket.processors;

import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.services.CallService;
import org.margin.server.social.calls.services.CallValidationService;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.stereotype.Component;

@Component
public class CallRejectProcessor implements WebSocketMessageProcessor<String> {
    private final CallService callService;
    private final WebSocketDeliveryService webSocketDeliveryService;
    private final CallValidationService callValidationService;

    public CallRejectProcessor(CallService callService, WebSocketDeliveryService webSocketDeliveryService, CallValidationService callValidationService) {
        this.callService = callService;
        this.webSocketDeliveryService = webSocketDeliveryService;
        this.callValidationService = callValidationService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_REJECTED;
    }

    @Override
    public void process(User user, WebSocketMessageIn<String> message) {
        Call call = callService.getById(Long.valueOf(message.getPayload()));
        callValidationService.validateUserIsReceiver(call, user);
        callService.rejectCall(call.getId());
        webSocketDeliveryService.notifyCallEnd(message.getRecipientId());
    }
}
