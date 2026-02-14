package org.margin.server.social.messages.models.dtos;

import org.margin.server.users.models.User;

import java.util.List;

public record MessageResult(
        MessageDTO message,
        List<User> recipients
) {}