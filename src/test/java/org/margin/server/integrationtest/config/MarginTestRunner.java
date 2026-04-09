package org.margin.server.integrationtest.config;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("integration")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class MarginTestRunner {

    protected static final int WS_PORT = 8081;

    @BeforeEach
    void waitForServer() throws Exception {
        waitForPort(WS_PORT, 5000);
    }

    @SuppressWarnings("BusyWait")
    private static void waitForPort(int port, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (var ignored = new java.net.Socket("localhost", port)) {
                return;
            } catch (Exception ignored) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Port " + port + " not ready after " + timeoutMs + "ms");
    }
}