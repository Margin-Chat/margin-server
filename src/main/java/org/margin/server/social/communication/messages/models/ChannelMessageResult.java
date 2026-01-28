package org.margin.server.social.communication.messages.models;

import org.margin.server.social.communication.messages.models.dtos.SpaceChannelMessageDTO;
import org.margin.server.users.models.User;

import java.util.List;

public record ChannelMessageResult(
        SpaceChannelMessageDTO message,
        List<User> recipients
) {}