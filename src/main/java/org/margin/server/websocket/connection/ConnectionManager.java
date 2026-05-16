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

    private final Map<Long, Set<ClientConnection>> connections = new ConcurrentHashMap<>();

    public void addConnection(User user, ClientConnection connection) {
        connections.computeIfAbsent(user.getId(), id -> ConcurrentHashMap.newKeySet()).add(connection);
        log.info("User {} connected (total sessions: {})", user.getId(), connections.get(user.getId()).size());
    }

    public boolean removeConnection(User user, Channel channel) {
        Set<ClientConnection> sessions = connections.get(user.getId());
        if (sessions == null) return false;
        sessions.removeIf(c -> c.getChannel() == channel);
        if (sessions.isEmpty()) {
            connections.remove(user.getId());
            return true;
        }
        return false;
    }

    public boolean isUserOnline(Long userId) {
        Set<ClientConnection> sessions = connections.get(userId);
        return sessions != null && sessions.stream().anyMatch(ClientConnection::isActive);
    }

    public void sendToUser(Long userId, String jsonMessage) {
        Set<ClientConnection> sessions = connections.get(userId);
        if (sessions == null) {
            log.debug("User {} not connected, cannot send message", userId);
            return;
        }
        sessions.stream()
                .filter(ClientConnection::isActive)
                .forEach(c -> c.sendMessage(jsonMessage));
    }

    public void broadcast(String jsonMessage, Long excludeUserId) {
        connections.forEach((userId, sessions) -> {
            if (!userId.equals(excludeUserId)) {
                sessions.stream()
                        .filter(ClientConnection::isActive)
                        .forEach(c -> c.sendMessage(jsonMessage));
            }
        });
    }

    public void closeAllSessions(Long userId) {
        Set<ClientConnection> sessions = connections.get(userId);
        if (sessions == null) return;
        new HashSet<>(sessions).forEach(ClientConnection::close);
        log.info("Closing {} session(s) for user {}", sessions.size(), userId);
    }

    public void clearAll() {
        connections.values().forEach(sessions -> sessions.forEach(ClientConnection::close));
        connections.clear();
    }

    public Set<Long> getOnlineUserIds() {
        return new HashSet<>(connections.keySet());
    }
}
