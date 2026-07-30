package org.margin.server.websocket.listeners;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.events.UserSessionsRevokedEvent;
import org.margin.server.websocket.connection.ConnectionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class AuthenticationWebSocketEventListener {

    private final ConnectionManager connectionManager;

    public AuthenticationWebSocketEventListener(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @TransactionalEventListener
    public void onUserSessionsRevoked(UserSessionsRevokedEvent event) {
        connectionManager.closeAllSessions(event.userId());
    }
}
