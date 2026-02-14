package org.margin.server.websocket.handlers;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.notifications.NotificationService;
import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.social.calls.models.CallType;
import org.margin.server.social.calls.services.CallService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.models.WebSocketMessageIn;
import org.margin.server.websocket.models.payloads.*;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CallHandler {

    private final CallService callService;
    private final NotificationService notificationService;
    private final UserService userService;

    public CallHandler(CallService callService, NotificationService notificationService, UserService userService) {
        this.callService = callService;
        this.notificationService = notificationService;
        this.userService = userService;
    }

    public void handleCallOffer(User fromUser, WebSocketMessageIn<IncomingCallOfferPayload> wsMessage) {
        IncomingCallOfferPayload payload = wsMessage.getPayload();

        Call call = callService.createCall(
                fromUser,
                userService.getById(wsMessage.getRecipientId()),
                CallStatus.OFFERED,
                CallType.AUDIO,
                payload.sdp()
        );

        notificationService.notifyCallOffer(
                wsMessage.getRecipientId(),
                call.getId(),
                fromUser.getId(),
                payload.sdp(),
                CallType.AUDIO.toString()
        );
    }

    public void handleCallResponse(WebSocketMessageIn<IncomingCallResponsePayload> wsMessage) {
        IncomingCallResponsePayload payload = wsMessage.getPayload();

        callService.updateCallStatus(payload.callId(), CallStatus.ACCEPTED);

        notificationService.notifyCallResponse(
                wsMessage.getRecipientId(),
                payload.callId(),
                payload.callerId(),
                payload.response()
        );
    }

    public void handleCallCandidate(WebSocketMessageIn<IncomingCallCandidatePayload> wsMessage) {
        notificationService.notifyCallCandidate(
                wsMessage.getRecipientId(),
                wsMessage.getPayload()
        );
    }

    public void handleCallEnd(WebSocketMessageIn<IncomingCallEndPayload> wsMessage) {
        IncomingCallEndPayload payload = wsMessage.getPayload();

        callService.endCall(payload.callId(), payload.callDuration());

        notificationService.notifyCallEnd(wsMessage.getRecipientId());

        log.debug("Call ended: {}", payload.callId());
    }
}