package org.margin.server.social.exceptions;

public class ChannelNotFoundException extends RuntimeException {
    public ChannelNotFoundException(Long channelId) {
        super("Channel not found: " + channelId);
    }
}