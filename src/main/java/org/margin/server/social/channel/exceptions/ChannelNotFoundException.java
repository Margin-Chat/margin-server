package org.margin.server.social.channel.exceptions;

import org.margin.server.shared.exceptions.DomainException;
import org.springframework.http.HttpStatus;

public class ChannelNotFoundException extends DomainException {
    public ChannelNotFoundException(Long channelId) {
        super(HttpStatus.NOT_FOUND, "Channel not found: " + channelId);
    }
}
