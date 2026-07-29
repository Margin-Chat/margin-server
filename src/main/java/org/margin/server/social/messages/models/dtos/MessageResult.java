package org.margin.server.social.messages.models.dtos;

import org.springframework.modulith.NamedInterface;


import java.util.List;

@NamedInterface("api")
public record MessageResult(
        MessageDTO message,
        List<Long> recipientIds
) {}