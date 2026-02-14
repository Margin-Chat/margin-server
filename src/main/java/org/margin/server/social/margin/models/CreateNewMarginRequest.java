package org.margin.server.social.margin.models;

public record CreateNewMarginRequest (
        String marginName,
        String marginDescription,
        String visibility
) {}

