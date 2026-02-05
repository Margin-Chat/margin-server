package org.margin.server.social.models.margin;

public record CreateNewMarginRequest (
        String marginName,
        String marginDescription,
        String visibility
) {}

