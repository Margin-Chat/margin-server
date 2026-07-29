package org.margin.server.social.api;

public interface ChannelDirectory {

    Long marginIdOf(Long channelId);

    Long conversationIdOf(Long channelId);
}
