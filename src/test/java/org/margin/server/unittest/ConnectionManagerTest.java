package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.connection.ClientConnection;
import org.margin.server.connection.ConnectionManager;
import org.margin.server.users.models.User;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionManagerTest {

    private ConnectionManager connectionManager;

    @Mock
    private ClientConnection mockConnection;

    @BeforeEach
    void setUp() {
        connectionManager = new ConnectionManager();
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    @Test
    @DisplayName("addConnection should store connection and isUserOnline should return true")
    void addConnection_Success() {
        User user = createUser(1L);
        when(mockConnection.isActive()).thenReturn(true);

        connectionManager.addConnection(user, mockConnection);

        assertTrue(connectionManager.isUserOnline(1L));
        assertEquals(mockConnection, connectionManager.getConnection(1L));
    }

    @Test
    @DisplayName("removeConnection should remove connection and isUserOnline should return false")
    void removeConnection_Success() {
        User user = createUser(1L);
        connectionManager.addConnection(user, mockConnection);

        connectionManager.removeConnection(user);

        assertFalse(connectionManager.isUserOnline(1L));
        assertNull(connectionManager.getConnection(1L));
    }

    @Test
    @DisplayName("sendToUser should send message only if connection is active")
    void sendToUser_SendsIfActive() {
        User user = createUser(1L);
        connectionManager.addConnection(user, mockConnection);
        when(mockConnection.isActive()).thenReturn(true);

        connectionManager.sendToUser(1L, "{\"msg\":\"hello\"}");

        verify(mockConnection).sendMessage("{\"msg\":\"hello\"}");
    }

    @Test
    @DisplayName("sendToUser should do nothing if connection is inactive")
    void sendToUser_DoesNothingIfInactive() {
        User user = createUser(1L);
        connectionManager.addConnection(user, mockConnection);
        when(mockConnection.isActive()).thenReturn(false);

        connectionManager.sendToUser(1L, "{\"msg\":\"hello\"}");

        verify(mockConnection, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("broadcast should send message to all users except excluded one")
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

        String msg = "broadcast-msg";
        connectionManager.broadcast(msg, 2L);

        verify(conn1).sendMessage(msg);
        verify(conn2, never()).sendMessage(anyString());
        verify(conn3).sendMessage(msg);
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