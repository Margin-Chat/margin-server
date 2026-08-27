package org.margin.server.meetings.api;

import io.netty.channel.Channel;

public interface MeetingLobbyRegistry {

    void register(Long meetingId, Long guestUserId, Channel channel);

    void unregister(Long meetingId, Long guestUserId);

    boolean send(Long meetingId, Long guestUserId, String json);
}
