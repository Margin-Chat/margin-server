package org.margin.server.social.api;

public interface ChannelLookup {

    Long marginIdOf(Long channelId);

    Long conversationIdOf(Long channelId);

    String nameOf(Long channelId);
}
