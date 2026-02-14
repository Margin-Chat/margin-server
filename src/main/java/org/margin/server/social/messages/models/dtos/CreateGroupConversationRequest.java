package org.margin.server.social.messages.models.dtos;

import java.util.List;

public record CreateGroupConversationRequest(
        List<Long> userIds,
        String name
) {}