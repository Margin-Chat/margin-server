package org.margin.server.integrationtest.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.social.calls.models.CallType;
import org.margin.server.social.calls.repositories.CallRepository;
import org.margin.server.social.calls.services.CallService;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CallTestUtils {

    private static CallService callService;
    private static CallRepository callRepository;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public CallTestUtils(CallService callService, CallRepository callRepository) {
        CallTestUtils.callService = callService;
        CallTestUtils.callRepository = callRepository;
    }

    public static Call createCall(User caller, User receiver) {
        return callService.createCall(caller, receiver, CallStatus.OFFERED, CallType.AUDIO);
    }

    public static Call createCall(User caller, User receiver, CallStatus status) {
        return callService.createCall(caller, receiver, status, CallType.AUDIO);
    }

    public static Call createCall(User caller, User receiver, CallStatus status, CallType type) {
        return callService.createCall(caller, receiver, status, type);
    }

    public static Call findById(Long callId) {
        return callRepository.findById(callId).orElseThrow();
    }

    public static long extractCallId(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            return json.path("payload").path("callId").asLong();
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract callId from message", e);
        }
    }

    public static String offerFrame(User receiver, String sdp) {
        return WebSocketFrameTestBuilder.ofType("CALL_OFFER")
                .recipientId(receiver.getId())
                .payload(WebSocketFrameTestBuilder.object()
                        .put("sdp", sdp))
                .build();
    }

    public static String offerFrame(User receiver) {
        return offerFrame(receiver, "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n");
    }

    public static String endFrame(User receiver, Long callId, int duration) {
        return WebSocketFrameTestBuilder.ofType("CALL_END")
                .recipientId(receiver.getId())
                .payload(WebSocketFrameTestBuilder.object()
                        .put("callId", callId)
                        .put("callDuration", duration))
                .build();
    }

    public static String rejectFrame(User caller, Long callId) {
        return WebSocketFrameTestBuilder.ofType("CALL_REJECTED")
                .recipientId(caller.getId())
                .payload(String.valueOf(callId))
                .build();
    }

    public static String candidateFrame(User receiver) {
        return WebSocketFrameTestBuilder.ofType("CALL_CANDIDATE")
                .recipientId(receiver.getId())
                .payload(WebSocketFrameTestBuilder.object()
                        .put("candidate", "candidate:1 1 UDP 2130706431 192.168.1.1 5000 typ host")
                        .put("sdpMid", "audio")
                        .put("sdpMLineIndex", 0))
                .build();
    }

    public static String responseFrame(User caller, Long callId, String sdp) {
        return WebSocketFrameTestBuilder.ofType("CALL_RESPONSE")
                .recipientId(caller.getId())
                .payload(WebSocketFrameTestBuilder.object()
                        .put("callId", callId)
                        .put("callerId", caller.getId())
                        .set("response", WebSocketFrameTestBuilder.object()
                                .put("type", "answer")
                                .put("sdp", sdp)))
                .build();
    }

    public static String responseFrame(User caller, Long callId) {
        return responseFrame(caller, callId, "v=0\r\no=- 456 2 IN IP4 127.0.0.1\r\n");
    }
}