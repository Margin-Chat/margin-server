package org.margin.server.websocket.connection;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ConnectionManager {

    private final Map<Long, ClientConnection> connections = new ConcurrentHashMap<>();

    public void addConnection(User user, ClientConnection connection) {
        ClientConnection old = connections.put(user.getId(), connection);
        if (old != null && old.isActive()) {
            log.info("Replacing existing connection for user {}, closing old channel", user.getId());
            old.close();
        }
    }

    public void removeConnection(User user) {
        connections.remove(user.getId());
    }

    public boolean isUserOnline(Long userId) {
        ClientConnection conn = connections.get(userId);
        return conn != null && conn.isActive();
    }

    public void sendToUser(Long userId, String jsonMessage) {
        ClientConnection connection = connections.get(userId);
        if (connection != null && connection.isActive()) {
            connection.sendMessage(jsonMessage);
        } else {
            log.debug("User {} not connected, cannot send message", userId);
        }
    }

    public void broadcast(String jsonMessage, Long excludeUserId) {
        connections.forEach((userId, connection) -> {
            if (!userId.equals(excludeUserId) && connection.isActive()) {
                connection.sendMessage(jsonMessage);
            }
        });
    }

    public void clearAll() {
        connections.values().forEach(ClientConnection::close);
        connections.clear();
    }

    public ClientConnection getConnection(Long userId) {
        return connections.get(userId);
    }

    public Set<Long> getOnlineUserIds() {
        return new HashSet<>(connections.keySet());
    }

    public boolean removeConnectionIfMatch(User user, Channel channel) {
        ClientConnection conn = connections.get(user.getId());
        if (conn != null && conn.getChannel() == channel) {
            connections.remove(user.getId());
            return true;
        }
        return false;
    }
}