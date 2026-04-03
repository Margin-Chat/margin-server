package org.margin.server.integrationtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.social.calls.repositories.CallRepository;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.margin.server.integrationtest.IntegrationTestHelper.*;

class CallOfferTest extends MarginTestRunner {

    @Autowired private JwtService jwtService;
    @Autowired private CallRepository callRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebSocket callerWs;
    private WebSocket receiverWs;

    @AfterEach
    void tearDown() {
        closeWebSocket(callerWs, receiverWs);
    }

    @Test
    void callOffer_persistsCallAndNotifiesBothParties() throws Exception {
        User caller = createUser("caller", "caller@margin.chat");
        User receiver = createUser("receiver", "receiver@margin.chat");

        CompletableFuture<String> callerReceived = new CompletableFuture<>();
        CompletableFuture<String> receiverReceived = new CompletableFuture<>();

        callerWs = connectWebSocket(WS_PORT, jwtService.generateToken(caller.getEmail(), caller.getId()),
                listenerThatCompletes(callerReceived, "CALL_CREATED"));
        receiverWs = connectWebSocket(WS_PORT, jwtService.generateToken(receiver.getEmail(), receiver.getId()),
                listenerThatCompletes(receiverReceived, "CALL_OFFER"));

        String offerFrame = """
                {
                  "type": "CALL_OFFER",
                  "recipientId": %d,
                  "payload": {
                    "sdp": "v=0\\r\\no=- 123 2 IN IP4 127.0.0.1\\r\\n"
                  }
                }
                """.formatted(receiver.getId());

        callerWs.sendText(offerFrame, true).get(5, TimeUnit.SECONDS);

        String callerMsg = callerReceived.get(5, TimeUnit.SECONDS);
        assertTrue(callerMsg.contains("CALL_CREATED"));

        String receiverMsg = receiverReceived.get(5, TimeUnit.SECONDS);
        assertTrue(receiverMsg.contains("CALL_OFFER"));
        assertTrue(receiverMsg.contains(caller.getUsername()));

        JsonNode callerJson = objectMapper.readTree(callerMsg);
        long callId = callerJson.path("payload").path("callId").asLong();

        Call call = callRepository.findById(callId).orElseThrow();
        assertEquals(CallStatus.OFFERED, call.getStatus());
        assertEquals(caller.getId(), call.getCaller().getId());
        assertEquals(receiver.getId(), call.getReceiver().getId());
    }

    @Test
    void callOffer_toOfflineUser_stillPersistsCall() throws Exception {
        User caller = createUser("caller", "caller@margin.chat");
        User offlineReceiver = createUser("offline", "offline@margin.chat");

        CompletableFuture<String> callerReceived = new CompletableFuture<>();

        callerWs = connectWebSocket(WS_PORT, jwtService.generateToken(caller.getEmail(), caller.getId()),
                listenerThatCompletes(callerReceived, "CALL_CREATED"));

        String offerFrame = """
                {
                  "type": "CALL_OFFER",
                  "recipientId": %d,
                  "payload": {
                    "sdp": "v=0\\r\\no=- 123 2 IN IP4 127.0.0.1\\r\\n"
                  }
                }
                """.formatted(offlineReceiver.getId());

        callerWs.sendText(offerFrame, true).get(5, TimeUnit.SECONDS);

        String callerMsg = callerReceived.get(5, TimeUnit.SECONDS);
        JsonNode json = objectMapper.readTree(callerMsg);
        long callId = json.path("payload").path("callId").asLong();

        Call call = callRepository.findById(callId).orElseThrow();
        assertEquals(CallStatus.OFFERED, call.getStatus());
    }
}