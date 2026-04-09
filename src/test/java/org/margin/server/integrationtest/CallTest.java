package org.margin.server.integrationtest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.CallTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.integrationtest.utils.WebSocketTestUtils;
import org.margin.server.social.calls.models.Call;
import org.margin.server.social.calls.models.CallStatus;
import org.margin.server.users.models.User;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CallTest extends MarginTestRunner {

    private WebSocket callerWs;
    private WebSocket receiverWs;

    @AfterEach
    void tearDown() {
        WebSocketTestUtils.close(callerWs, receiverWs);
    }

    @Test
    void callOffer_persistsCallAndNotifiesBothParties() throws Exception {
        User caller = UserTestUtils.createUser("caller", "caller@margin.chat");
        User receiver = UserTestUtils.createUser("receiver", "receiver@margin.chat");

        CompletableFuture<String> callerReceived = new CompletableFuture<>();
        CompletableFuture<String> receiverReceived = new CompletableFuture<>();

        callerWs = WebSocketTestUtils.connect(caller,
                WebSocketTestUtils.listenerThatCompletes(callerReceived, "CALL_CREATED"));
        receiverWs = WebSocketTestUtils.connect(receiver,
                WebSocketTestUtils.listenerThatCompletes(receiverReceived, "CALL_OFFER"));

        callerWs.sendText(CallTestUtils.offerFrame(receiver), true).get(5, TimeUnit.SECONDS);

        String callerMsg = callerReceived.get(5, TimeUnit.SECONDS);
        assertTrue(callerMsg.contains("CALL_CREATED"));

        String receiverMsg = receiverReceived.get(5, TimeUnit.SECONDS);
        assertTrue(receiverMsg.contains("CALL_OFFER"));

        long callId = CallTestUtils.extractCallId(callerMsg);
        Call call = CallTestUtils.findById(callId);
        assertEquals(CallStatus.OFFERED, call.getStatus());
        assertEquals(caller.getId(), call.getCaller().getId());
        assertEquals(receiver.getId(), call.getReceiver().getId());
    }

    @Test
    void callOffer_toOfflineUser_stillPersistsCall() throws Exception {
        User caller = UserTestUtils.createUser("caller", "caller@margin.chat");
        User offline = UserTestUtils.createUser("offline", "offline@margin.chat");

        CompletableFuture<String> callerReceived = new CompletableFuture<>();
        callerWs = WebSocketTestUtils.connect(caller,
                WebSocketTestUtils.listenerThatCompletes(callerReceived, "CALL_CREATED"));

        callerWs.sendText(CallTestUtils.offerFrame(offline), true).get(5, TimeUnit.SECONDS);

        String callerMsg = callerReceived.get(5, TimeUnit.SECONDS);
        long callId = CallTestUtils.extractCallId(callerMsg);

        Call call = CallTestUtils.findById(callId);
        assertEquals(CallStatus.OFFERED, call.getStatus());
    }

    @Test
    void callEnd_persistsDurationAndNotifiesOtherParty() throws Exception {
        User caller = UserTestUtils.createUser("caller", "caller@margin.chat");
        User receiver = UserTestUtils.createUser("receiver", "receiver@margin.chat");
        Call call = CallTestUtils.createCall(caller, receiver);

        CompletableFuture<String> receiverReceived = new CompletableFuture<>();

        callerWs = WebSocketTestUtils.connect(caller);
        receiverWs = WebSocketTestUtils.connect(receiver,
                WebSocketTestUtils.listenerThatCompletes(receiverReceived, "CALL_END"));

        callerWs.sendText(CallTestUtils.endFrame(receiver, call.getId(), 45), true)
                .get(5, TimeUnit.SECONDS);

        String receiverMsg = receiverReceived.get(5, TimeUnit.SECONDS);
        assertTrue(receiverMsg.contains("CALL_END"));

        Call ended = CallTestUtils.findById(call.getId());
        assertEquals(CallStatus.ENDED, ended.getStatus());
        assertEquals(45, ended.getDurationSeconds());
        assertNotNull(ended.getEndedAt());
    }

    @Test
    void callRejected_updatesStatusInDatabase() throws Exception {
        User caller = UserTestUtils.createUser("caller", "caller@margin.chat");
        User receiver = UserTestUtils.createUser("receiver", "receiver@margin.chat");
        Call call = CallTestUtils.createCall(caller, receiver);

        receiverWs = WebSocketTestUtils.connect(receiver);

        receiverWs.sendText(CallTestUtils.rejectFrame(caller, call.getId()), true)
                .get(5, TimeUnit.SECONDS);

        Thread.sleep(500);

        Call rejected = CallTestUtils.findById(call.getId());
        assertEquals(CallStatus.REJECTED, rejected.getStatus());
        assertNotNull(rejected.getEndedAt());
        assertEquals(0, rejected.getDurationSeconds());
    }

    @Test
    void callCandidate_forwardsToRecipient() throws Exception {
        User caller = UserTestUtils.createUser("caller", "caller@margin.chat");
        User receiver = UserTestUtils.createUser("receiver", "receiver@margin.chat");

        CompletableFuture<String> receiverReceived = new CompletableFuture<>();

        callerWs = WebSocketTestUtils.connect(caller);
        receiverWs = WebSocketTestUtils.connect(receiver,
                WebSocketTestUtils.listenerThatCompletes(receiverReceived, "CALL_CANDIDATE"));

        callerWs.sendText(CallTestUtils.candidateFrame(receiver), true).get(5, TimeUnit.SECONDS);

        String receiverMsg = receiverReceived.get(5, TimeUnit.SECONDS);
        assertTrue(receiverMsg.contains("CALL_CANDIDATE"));
        assertTrue(receiverMsg.contains("candidate:1"));
    }

    @Test
    void callResponse_forwardsToCaller() throws Exception {
        User caller = UserTestUtils.createUser("caller", "caller@margin.chat");
        User receiver = UserTestUtils.createUser("receiver", "receiver@margin.chat");
        Call call = CallTestUtils.createCall(caller, receiver);

        CompletableFuture<String> callerReceived = new CompletableFuture<>();

        callerWs = WebSocketTestUtils.connect(caller,
                WebSocketTestUtils.listenerThatCompletes(callerReceived, "CALL_RESPONSE"));
        receiverWs = WebSocketTestUtils.connect(receiver);

        receiverWs.sendText(CallTestUtils.responseFrame(caller, call.getId()), true)
                .get(5, TimeUnit.SECONDS);

        String callerMsg = callerReceived.get(5, TimeUnit.SECONDS);
        assertTrue(callerMsg.contains("CALL_RESPONSE"));

        Call accepted = CallTestUtils.findById(call.getId());
        assertEquals(CallStatus.ACCEPTED, accepted.getStatus());
    }
}