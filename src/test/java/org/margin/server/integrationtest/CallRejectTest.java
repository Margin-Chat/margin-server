package org.margin.server.integrationtest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.social.calls.models.CallType;
import org.margin.server.social.calls.repositories.CallRepository;
import org.margin.server.social.calls.services.CallService;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.WebSocket;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.margin.server.integrationtest.IntegrationTestHelper.*;

class CallRejectTest extends MarginTestRunner {

    @Autowired private JwtService jwtService;
    @Autowired private CallService callService;
    @Autowired private CallRepository callRepository;

    private WebSocket receiverWs;

    @AfterEach
    void tearDown() {
        closeWebSocket(receiverWs);
    }

    @Test
    void callRejected_updatesStatusInDatabase() throws Exception {
        User caller = createUser("caller", "caller@margin.chat");
        User receiver = createUser("receiver", "receiver@margin.chat");
        Call call = callService.createCall(caller, receiver, CallStatus.OFFERED, CallType.AUDIO);

        receiverWs = connectWebSocket(WS_PORT, jwtService.generateToken(receiver.getEmail(), receiver.getId()),
                new WebSocket.Listener() {});

        String rejectFrame = """
                {
                  "type": "CALL_REJECTED",
                  "recipientId": %d,
                  "payload": "%d"
                }
                """.formatted(caller.getId(), call.getId());

        receiverWs.sendText(rejectFrame, true).get(5, TimeUnit.SECONDS);

        Thread.sleep(500);

        Call rejected = callRepository.findById(call.getId()).orElseThrow();
        assertEquals(CallStatus.REJECTED, rejected.getStatus());
        assertNotNull(rejected.getEndedAt());
        assertEquals(0, rejected.getDurationSeconds());
    }
}