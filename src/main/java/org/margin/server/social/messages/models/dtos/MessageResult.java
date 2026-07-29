package org.margin.server.social.messages.models.dtos;


import java.util.List;

public record MessageResult(
        MessageDTO message,
        List<Long> recipientIds
) {}