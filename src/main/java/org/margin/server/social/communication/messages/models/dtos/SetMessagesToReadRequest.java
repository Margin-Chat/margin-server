package org.margin.server.social.communication.messages.models.dtos;

import java.util.List;

public record SetMessagesToReadRequest(
        Long fromUserId,
        Long toUserId,
        List<Long> messageIds
) {}