package org.margin.server.unittest;

import io.netty.channel.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.websocket.connection.ClientConnection;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.users.models.User;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.margin.server.unittest.utils.UserTestUtils.*;

@ExtendWith(MockitoExtension.class)
class ConnectionManagerTest {

    private ConnectionManager connectionManager;

    @Mock private ClientConnection mockConnection;
    @Mock private Channel mockChannel;

    @BeforeEach
    void setUp() {
        connectionManager = new ConnectionManager();
    }

    private ClientConnection activeConnection(Channel channel) {
        ClientConnection conn = mock(ClientConnection.class);
        lenient().when(conn.isActive()).thenReturn(true);
        when(conn.getChannel()).thenReturn(channel);
        return conn;
    }

    @Test
    @DisplayName("addConnection should store connection and isUserOnline should return true")
    void addConnection_Success() {
        User user = createUser(1L);
        when(mockConnection.isActive()).thenReturn(true);

        connectionManager.addConnection(user, mockConnection);

        assertTrue(connectionManager.isUserOnline(1L));
    }

    @Test
    @DisplayName("addConnection should allow multiple sessions for the same user")
    void addConnection_MultipleSessions() {
        User user = createUser(1L);
        ClientConnection session1 = mock(ClientConnection.class);
        ClientConnection session2 = mock(ClientConnection.class);
        when(session1.isActive()).thenReturn(true);

        connectionManager.addConnection(user, session1);
        connectionManager.addConnection(user, session2);

        assertTrue(connectionManager.isUserOnline(1L));
    }

    @Test
    @DisplayName("removeConnection should return false and keep user online when other sessions remain")
    void removeConnection_NotLastSession_ReturnsFalse() {
        User user = createUser(1L);
        Channel channel1 = mock(Channel.class);
        Channel channel2 = mock(Channel.class);
        ClientConnection conn1 = activeConnection(channel1);
        ClientConnection conn2 = activeConnection(channel2);

        connectionManager.addConnection(user, conn1);
        connectionManager.addConnection(user, conn2);

        boolean wasLast = connectionManager.removeConnection(user, channel1);

        assertFalse(wasLast);
        assertTrue(connectionManager.isUserOnline(1L));
    }

    @Test
    @DisplayName("removeConnection should return true and mark user offline when last session closes")
    void removeConnection_LastSession_ReturnsTrue() {
        User user = createUser(1L);
        when(mockConnection.getChannel()).thenReturn(mockChannel);

        connectionManager.addConnection(user, mockConnection);

        boolean wasLast = connectionManager.removeConnection(user, mockChannel);

        assertTrue(wasLast);
        assertFalse(connectionManager.isUserOnline(1L));
    }

    @Test
    @DisplayName("sendToUser should send message to all active sessions")
    void sendToUser_SendsToAllActiveSessions() {
        User user = createUser(1L);
        ClientConnection conn1 = mock(ClientConnection.class);
        ClientConnection conn2 = mock(ClientConnection.class);
        when(conn1.isActive()).thenReturn(true);
        when(conn2.isActive()).thenReturn(true);

        connectionManager.addConnection(user, conn1);
        connectionManager.addConnection(user, conn2);

        connectionManager.sendToUser(1L, "{\"msg\":\"hello\"}");

        verify(conn1).sendMessage("{\"msg\":\"hello\"}");
        verify(conn2).sendMessage("{\"msg\":\"hello\"}");
    }

    @Test
    @DisplayName("sendToUser should skip inactive sessions")
    void sendToUser_SkipsInactiveSessions() {
        User user = createUser(1L);
        ClientConnection active = mock(ClientConnection.class);
        ClientConnection inactive = mock(ClientConnection.class);
        when(active.isActive()).thenReturn(true);
        when(inactive.isActive()).thenReturn(false);

        connectionManager.addConnection(user, active);
        connectionManager.addConnection(user, inactive);

        connectionManager.sendToUser(1L, "{\"msg\":\"hello\"}");

        verify(active).sendMessage("{\"msg\":\"hello\"}");
        verify(inactive, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("broadcast should send to all sessions of all users except the excluded one")
    void broadcast_SendsToOthers() {
        User user1 = createUser(1L);
        User user2 = createUser(2L);
        User user3 = createUser(3L);

        ClientConnection conn1 = mock(ClientConnection.class);
        ClientConnection conn2 = mock(ClientConnection.class);
        ClientConnection conn3 = mock(ClientConnection.class);

        when(conn1.isActive()).thenReturn(true);
        lenient().when(conn2.isActive()).thenReturn(true);
        when(conn3.isActive()).thenReturn(true);

        connectionManager.addConnection(user1, conn1);
        connectionManager.addConnection(user2, conn2);
        connectionManager.addConnection(user3, conn3);

        connectionManager.broadcast("broadcast-msg", 2L);

        verify(conn1).sendMessage("broadcast-msg");
        verify(conn2, never()).sendMessage(anyString());
        verify(conn3).sendMessage("broadcast-msg");
    }

    @Test
    @DisplayName("closeAllSessions should close every session for the user")
    void closeAllSessions_ClosesAllSessions() {
        User user = createUser(1L);
        ClientConnection conn1 = mock(ClientConnection.class);
        ClientConnection conn2 = mock(ClientConnection.class);

        connectionManager.addConnection(user, conn1);
        connectionManager.addConnection(user, conn2);

        connectionManager.closeAllSessions(1L);

        assertFalse(connectionManager.isUserOnline(1L));
        verify(conn1).close();
        verify(conn2).close();
    }

    @Test
    @DisplayName("clearAll should close all connections and clear map")
    void clearAll_ClosesAndClears() {
        connectionManager.addConnection(createUser(1L), mockConnection);
        ClientConnection mockConnection2 = mock(ClientConnection.class);
        connectionManager.addConnection(createUser(2L), mockConnection2);

        connectionManager.clearAll();

        assertTrue(connectionManager.getOnlineUserIds().isEmpty());
        verify(mockConnection).close();
        verify(mockConnection2).close();
    }
}
