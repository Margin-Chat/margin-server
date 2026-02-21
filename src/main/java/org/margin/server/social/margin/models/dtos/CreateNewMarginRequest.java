package org.margin.server.social.margin.models.dtos;

public record CreateNewMarginRequest (
        String marginName,
        String marginDescription,
        String visibility
) {}

