package org.margin.server.integrationtest.config;

import org.junit.jupiter.api.BeforeEach;
import org.margin.server.subscriptions.services.MollieClient;
import org.margin.server.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Import({TestcontainersConfig.class, CapturingPushSenderConfig.class})
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class MarginTestRunner {

    @Autowired
    protected WebSocketServer webSocketServer;

    @MockitoBean
    protected MollieClient mollieClient;

    @BeforeEach
    void waitForServer() throws Exception {
        webSocketServer.awaitBoundPort(5000);
    }
}
