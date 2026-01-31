package org.margin.server.social.communication.messages.models.dtos;

import org.margin.server.users.models.User;

import java.util.List;

public record ChannelMessageResult(
        ChannelMessageDTO message,
        List<User> recipients
) {}