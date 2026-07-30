package org.margin.server.websocket.listeners;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.calls.events.*;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.models.payloads.CallOfferPayload;
import org.margin.server.websocket.models.payloads.CallResponsePayload;
import org.margin.server.social.calls.models.CallCandidate;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Slf4j
@Component
public class CallWebSocketEventListener {
    private final WebSocketMessageBuilder webSocketMessageBuilder;
    private final ConnectionManager connectionManager;
    private final UserService userService;

    public CallWebSocketEventListener(WebSocketMessageBuilder webSocketMessageBuilder,
                                      ConnectionManager connectionManager, UserService userService) {
        this.webSocketMessageBuilder = webSocketMessageBuilder;
        this.connectionManager = connectionManager;
        this.userService = userService;
    }

    @EventListener
    public void onMissedCall(MissedCallEvent missedCallEvent) {
        String json = webSocketMessageBuilder.buildMessage(
                WebSocketMessageType.CALL_NO_ANSWER,
                missedCallEvent.getRecipientId(),
                null);
        connectionManager.sendToUser(missedCallEvent.getRecipientId(), json);
    }

    @EventListener
    public void onCallOffered(CallOfferedEvent callOfferedEvent) {
        var payload = new CallOfferPayload(
                callOfferedEvent.getCallId(),
                userService.toDTO(userService.getById(callOfferedEvent.getCallerId())),
                callOfferedEvent.getSdp(),
                callOfferedEvent.getCallType().toString()
        );
        String json = webSocketMessageBuilder.buildMessage(WebSocketMessageType.CALL_OFFER,
                payload.caller().id(), payload);

        connectionManager.sendToUser(callOfferedEvent.getRecipientId(), json);

        Map<String, Object> callCreatedPayload = Map.of("callId", callOfferedEvent.getCallId());
        String jsonCreated = webSocketMessageBuilder.buildMessage(WebSocketMessageType.CALL_CREATED,
                payload.caller().id(), callCreatedPayload);
        connectionManager.sendToUser(payload.caller().id(), jsonCreated);
    }

    @EventListener
    public void onCallAnswered(CallAnsweredEvent callAnsweredEvent) {
        var responsePayload = new CallResponsePayload(
                callAnsweredEvent.getCallId(),
                callAnsweredEvent.getCallerId(),
                callAnsweredEvent.getCallSessionDescription()
        );
        String json = webSocketMessageBuilder.buildMessage(WebSocketMessageType.CALL_RESPONSE,
                callAnsweredEvent.getRecipientId(), responsePayload);

        connectionManager.sendToUser(callAnsweredEvent.getRecipientId(), json);
    }

    @EventListener
    public void onCallCreated(CallCreatedEvent callCreatedEvent) {
        var payload = Map.of("callId", callCreatedEvent.getCallId());
        String json = webSocketMessageBuilder.buildMessage(WebSocketMessageType.CALL_CREATED,
                callCreatedEvent.getCallerId(), payload);

        connectionManager.sendToUser(callCreatedEvent.getCallerId(), json);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCallEnded(CallEndedEvent callEndedEvent) {
        String json = webSocketMessageBuilder.buildMessage(WebSocketMessageType.CALL_END,
                callEndedEvent.getRecipientId(), Map.of("callId", callEndedEvent.getCallId()));
        connectionManager.sendToUser(callEndedEvent.getRecipientId(), json);
    }

    @EventListener
    public void onCallMediaStateChanged(CallMediaStateEvent callMediaStateEvent) {
        String json = webSocketMessageBuilder.buildMessage(
                WebSocketMessageType.CALL_MEDIA_STATE,
                callMediaStateEvent.getRecipientId(),
                callMediaStateEvent.getMediaStatePayload());

        connectionManager.sendToUser(callMediaStateEvent.getRecipientId(), json);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCallResponseForwarded(CallResponseForwardedEvent event) {
        var payload = new CallResponsePayload(event.getCallId(), event.getCallerId(), event.getResponse());
        String json = webSocketMessageBuilder.buildMessage(WebSocketMessageType.CALL_RESPONSE, event.getRecipientId(), payload);
        connectionManager.sendToUser(event.getRecipientId(), json);
    }

    @EventListener
    public void onCallCandidateForwarded(CallCandidateForwardedEvent event) {
        CallCandidate payload = event.getPayload();
        String json = webSocketMessageBuilder.buildMessage(WebSocketMessageType.CALL_CANDIDATE, event.getRecipientId(), payload);
        connectionManager.sendToUser(event.getRecipientId(), json);
    }
}
