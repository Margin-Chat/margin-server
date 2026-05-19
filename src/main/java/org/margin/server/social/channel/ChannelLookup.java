package org.margin.server.social.channel;

import org.margin.server.social.channel.entities.Channel;

public interface ChannelLookup {
    Channel getById(Long channelId);
}