package org.margin.server.integrationtest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.integrationtest.utils.UserUtils;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.margin.server.integrationtest.IntegrationTestHelper.*;

class CallCandidateTest extends MarginTestRunner {

    @Autowired
    private JwtService jwtService;

    private WebSocket callerWs;
    private WebSocket receiverWs;

    @AfterEach
    void tearDown() {
        closeWebSocket(callerWs, receiverWs);
    }

    @Test
    void callCandidate_forwardsToRecipient() throws Exception {
        User caller = UserUtils.createUser("caller", "caller@margin.chat");
        User receiver = UserUtils.createUser("receiver", "receiver@margin.chat");

        CompletableFuture<String> receiverReceived = new CompletableFuture<>();

        callerWs = connectWebSocket(WS_PORT, jwtService.generateToken(caller.getEmail(), caller.getId()),
                new WebSocket.Listener() {
                });
        receiverWs = connectWebSocket(WS_PORT, jwtService.generateToken(receiver.getEmail(), receiver.getId()),
                listenerThatCompletes(receiverReceived, "CALL_CANDIDATE"));

        String candidateFrame = """
                {
                  "type": "CALL_CANDIDATE",
                  "recipientId": %d,
                  "payload": {
                    "candidate": "candidate:1 1 UDP 2130706431 192.168.1.1 5000 typ host",
                    "sdpMid": "audio",
                    "sdpMLineIndex": 0
                  }
                }
                """.formatted(receiver.getId());

        callerWs.sendText(candidateFrame, true).get(5, TimeUnit.SECONDS);

        String receiverMsg = receiverReceived.get(5, TimeUnit.SECONDS);
        assertTrue(receiverMsg.contains("CALL_CANDIDATE"));
        assertTrue(receiverMsg.contains("candidate:1"));
    }
}