package org.margin.server.integrationtest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.integrationtest.utils.WebSocketTestUtils;
import org.margin.server.users.models.User;
import org.margin.server.websocket.WebSocketHandler;
import org.margin.server.websocket.connection.ConnectionManager;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.http.WebSocket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The handshake is refused with close code 4001 rather than an HTTP 401 so that browser clients,
 * which cannot read the status of a response that never upgraded, can tell an auth failure apart
 * from a transport fault and stop reconnecting.
 */
class WebSocketAuthenticationTest extends MarginTestRunner {

    private static final int UNAUTHORIZED = 4001;

    @Autowired
    private ConnectionManager connectionManager;

    private User user;
    private WebSocket ws;
    private ListAppender<ILoggingEvent> logs;
    private Logger handlerLogger;

    @BeforeEach
    void setUp() {
        user = UserTestUtils.createUser("wsuser", "wsuser@margin.chat");
        logs = new ListAppender<>();
        logs.start();
        handlerLogger = (Logger) LoggerFactory.getLogger(WebSocketHandler.class);
        handlerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        WebSocketTestUtils.close(ws);
        handlerLogger.detachAppender(logs);
    }

    private boolean loggedAtError() {
        return logs.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    @DisplayName("expired token is rejected with close code 4001")
    void expiredToken_ClosesWith4001() throws Exception {
        String expired = WebSocketTestUtils.expiredToken(user, Duration.ofHours(1));

        WebSocketTestUtils.Rejection rejection = WebSocketTestUtils.connectExpectingRejection(expired);

        assertEquals(UNAUTHORIZED, rejection.statusCode());
    }

    @Test
    @DisplayName("missing token is rejected with close code 4001")
    void missingToken_ClosesWith4001() throws Exception {
        WebSocketTestUtils.Rejection rejection = WebSocketTestUtils.connectExpectingRejection(null);

        assertEquals(UNAUTHORIZED, rejection.statusCode());
    }

    @Test
    @DisplayName("empty token is rejected with close code 4001")
    void emptyToken_ClosesWith4001() throws Exception {
        WebSocketTestUtils.Rejection rejection = WebSocketTestUtils.connectExpectingRejection("");

        assertEquals(UNAUTHORIZED, rejection.statusCode());
    }

    @Test
    @DisplayName("token with an invalid signature is rejected with close code 4001")
    void tamperedToken_ClosesWith4001() throws Exception {
        String tampered = WebSocketTestUtils.validToken(user) + "tampered";

        WebSocketTestUtils.Rejection rejection = WebSocketTestUtils.connectExpectingRejection(tampered);

        assertEquals(UNAUTHORIZED, rejection.statusCode());
    }

    @Test
    @DisplayName("a rejected handshake does not register a connection or mark the user online")
    void rejectedHandshake_DoesNotRegisterConnection() throws Exception {
        String expired = WebSocketTestUtils.expiredToken(user, Duration.ofHours(1));

        WebSocketTestUtils.connectExpectingRejection(expired);

        assertFalse(connectionManager.isUserOnline(user.getId()));
    }

    @Test
    @DisplayName("a valid token still completes the handshake and registers the connection")
    void validToken_ConnectsSuccessfully() throws Exception {
        ws = WebSocketTestUtils.connect(user);

        assertTrue(connectionManager.isUserOnline(user.getId()));
    }

    @Test
    @DisplayName("a plain HTTP request gets a 400 rather than a failed handshake")
    void plainHttpRequest_IsRejectedWithoutHandshaking() throws Exception {
        // Netty assumes draft-00 when there is no version header, so it hands back a handshaker
        // for a request that is not an upgrade at all and then throws on the missing Upgrade.
        assertEquals(400, WebSocketTestUtils.plainHttpStatus("/ws"));
        assertFalse(loggedAtError(), "a non-WebSocket request must not be logged as an error");
    }

    @Test
    @DisplayName("a plain HTTP request carrying a valid token is still not a handshake")
    void plainHttpRequestWithToken_IsRejectedWithoutHandshaking() throws Exception {
        String token = WebSocketTestUtils.validTokenFor(user);

        assertEquals(400, WebSocketTestUtils.plainHttpStatus("/ws?token=" + token));
        assertFalse(loggedAtError());
        assertFalse(connectionManager.isUserOnline(user.getId()));
    }
}
