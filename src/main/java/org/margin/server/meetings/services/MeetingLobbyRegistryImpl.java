package org.margin.server.meetings.services;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.meetings.api.MeetingLobbyRegistry;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MeetingLobbyRegistryImpl implements MeetingLobbyRegistry {

    private final Map<Long, Map<Long, Channel>> lobbies = new ConcurrentHashMap<>();

    @Override
    public void register(Long meetingId, Long guestUserId, Channel channel) {
        lobbies.computeIfAbsent(meetingId, id -> new ConcurrentHashMap<>()).put(guestUserId, channel);
    }

    @Override
    public void unregister(Long meetingId, Long guestUserId) {
        Map<Long, Channel> lobby = lobbies.get(meetingId);
        if (lobby == null) {
            return;
        }
        lobby.remove(guestUserId);
        if (lobby.isEmpty()) {
            lobbies.remove(meetingId);
        }
    }

    @Override
    public boolean send(Long meetingId, Long guestUserId, String json) {
        Map<Long, Channel> lobby = lobbies.get(meetingId);
        if (lobby == null) {
            return false;
        }
        Channel channel = lobby.get(guestUserId);
        if (channel == null || !channel.isActive()) {
            return false;
        }
        channel.writeAndFlush(new TextWebSocketFrame(json));
        return true;
    }
}
