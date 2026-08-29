package org.margin.server.unittest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.shared.exceptions.GlobalExceptionHandler;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A client that hangs up mid-response is a fact of mobile life, not a server fault. These pin the
 * distinction: disconnects stay quiet, real failures still shout.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @Mock
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        logs = new ListAppender<>();
        logs.start();
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logger.setLevel(Level.DEBUG);
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    private boolean loggedAt(Level level) {
        return logs.list.stream().anyMatch(event -> event.getLevel() == level);
    }

    /** The chain from the production log: Jackson wrapping Spring wrapping Tomcat wrapping NIO. */
    private HttpMessageNotWritableException brokenPipeDuringSerialization() {
        IOException brokenPipe = new IOException("Broken pipe");
        ClientAbortException aborted = new ClientAbortException(brokenPipe);
        AsyncRequestNotUsableException unusable =
                new AsyncRequestNotUsableException("ServletOutputStream failed to write", aborted);
        return new HttpMessageNotWritableException("Could not write JSON", unusable);
    }

    @Test
    @DisplayName("a broken pipe mid-serialization is logged at debug and writes no error response")
    void brokenPipe_IsQuietAndWritesNothing() throws IOException {
        handler.handleUnwritableResponse(brokenPipeDuringSerialization(), response);

        assertFalse(loggedAt(Level.ERROR), "a client disconnect must not be logged as an error");
        assertTrue(loggedAt(Level.DEBUG));
        verify(response, never()).sendError(anyInt());
    }

    @Test
    @DisplayName("a genuine serialization failure is still logged as an error and sends a 500")
    void serializationFailure_IsReportedAndSends500() throws IOException {
        when(response.isCommitted()).thenReturn(false);
        HttpMessageNotWritableException ex = new HttpMessageNotWritableException(
                "Could not write JSON", new IllegalArgumentException("no serializer for type"));

        handler.handleUnwritableResponse(ex, response);

        assertTrue(loggedAt(Level.ERROR));
        verify(response).sendError(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @Test
    @DisplayName("a committed response is not written to again")
    void serializationFailure_DoesNotWriteToACommittedResponse() throws IOException {
        when(response.isCommitted()).thenReturn(true);
        HttpMessageNotWritableException ex = new HttpMessageNotWritableException(
                "Could not write JSON", new IllegalArgumentException("boom"));

        handler.handleUnwritableResponse(ex, response);

        verify(response, never()).sendError(anyInt());
    }

    @Test
    @DisplayName("a bare connection reset counts as a disconnect")
    void connectionReset_IsTreatedAsADisconnect() throws IOException {
        handler.handleUnwritableResponse(new IOException("Connection reset by peer"), response);

        assertFalse(loggedAt(Level.ERROR));
        verify(response, never()).sendError(anyInt());
    }

    @Test
    @DisplayName("disconnect exceptions raised directly are handled without a response write")
    void directDisconnectExceptions_AreQuiet() {
        assertDoesNotThrow(() ->
                handler.handleClientDisconnect(new ClientAbortException(new IOException("Broken pipe"))));
        assertDoesNotThrow(() ->
                handler.handleClientDisconnect(new AsyncRequestNotUsableException("Response not usable")));

        assertFalse(loggedAt(Level.ERROR));
        verifyNoInteractions(response);
    }

    @Test
    @DisplayName("the catch-all still reports real faults as errors and returns a 500 problem detail")
    void catchAll_ReportsRealFaults() {
        ProblemDetail detail = handler.handleUnexpected(new IllegalStateException("something broke"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), detail.getStatus());
        assertEquals("An unexpected error occurred", detail.getDetail());
        assertTrue(loggedAt(Level.ERROR));
    }

    @Test
    @DisplayName("a disconnect that reaches the catch-all is downgraded from error to debug")
    void catchAll_DowngradesDisconnects() {
        ProblemDetail detail = handler.handleUnexpected(brokenPipeDuringSerialization());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), detail.getStatus());
        assertFalse(loggedAt(Level.ERROR), "a client disconnect must not be logged as an error");
    }

    @Test
    @DisplayName("a cyclic cause chain terminates instead of spinning")
    void cyclicCauseChain_Terminates() {
        Exception first = new Exception("first");
        Exception second = new Exception("second");
        first.initCause(second);
        second.initCause(first);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> handler.handleUnexpected(first));
    }
}
