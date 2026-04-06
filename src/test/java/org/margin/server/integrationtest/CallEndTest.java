package org.margin.server.integrationtest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.integrationtest.utils.UserUtils;
import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.social.calls.models.CallType;
import org.margin.server.social.calls.repositories.CallRepository;
import org.margin.server.social.calls.services.CallService;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.margin.server.integrationtest.IntegrationTestHelper.*;

class CallEndTest extends MarginTestRunner {

    @Autowired
    private JwtService jwtService;
    @Autowired
    private CallService callService;
    @Autowired
    private CallRepository callRepository;

    private WebSocket callerWs;
    private WebSocket receiverWs;

    @AfterEach
    void tearDown() {
        closeWebSocket(callerWs, receiverWs);
    }

    @Test
    void callEnd_persistsDurationAndNotifiesOtherParty() throws Exception {
        User caller = UserUtils.createUser("caller", "caller@margin.chat");
        User receiver = UserUtils.createUser("receiver", "receiver@margin.chat");
        Call call = callService.createCall(caller, receiver, CallStatus.OFFERED, CallType.AUDIO);

        CompletableFuture<String> receiverReceived = new CompletableFuture<>();

        callerWs = connectWebSocket(WS_PORT, jwtService.generateToken(caller.getEmail(), caller.getId()),
                new WebSocket.Listener() {
                });
        receiverWs = connectWebSocket(WS_PORT, jwtService.generateToken(receiver.getEmail(), receiver.getId()),
                listenerThatCompletes(receiverReceived, "CALL_END"));

        String endFrame = """
                {
                  "type": "CALL_END",
                  "recipientId": %d,
                  "payload": {
                    "callId": %d,
                    "callDuration": 45
                  }
                }
                """.formatted(receiver.getId(), call.getId());

        callerWs.sendText(endFrame, true).get(5, TimeUnit.SECONDS);

        String receiverMsg = receiverReceived.get(5, TimeUnit.SECONDS);
        assertTrue(receiverMsg.contains("CALL_END"));

        Thread.sleep(500);

        Call ended = callRepository.findById(call.getId()).orElseThrow();
        assertEquals(CallStatus.ENDED, ended.getStatus());
        assertEquals(45, ended.getDurationSeconds());
        assertNotNull(ended.getEndedAt());
    }
}