package org.margin.server.websocket.processors;

import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.CallMediaStatePayload;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.stereotype.Component;

@Component
public class CallMediaStateProcessor implements WebSocketMessageProcessor<CallMediaStatePayload> {

    private final WebSocketDeliveryService webSocketDeliveryService;

    public CallMediaStateProcessor(WebSocketDeliveryService webSocketDeliveryService) {
        this.webSocketDeliveryService = webSocketDeliveryService;
    }

    @Override
    public WebSocketMessageType getType() {
        return WebSocketMessageType.CALL_MEDIA_STATE;
    }

    @Override
    public void process(User user, WebSocketMessageIn<CallMediaStatePayload> message) {
        webSocketDeliveryService.notifyCallMediaState(message.getRecipientId(), message.getPayload());
    }
}