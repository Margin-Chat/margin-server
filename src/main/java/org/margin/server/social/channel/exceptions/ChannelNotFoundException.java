package org.margin.server.social.channel.exceptions;

public class ChannelNotFoundException extends RuntimeException {
    public ChannelNotFoundException(Long channelId) {
        super("Channel not found: " + channelId);
    }
}