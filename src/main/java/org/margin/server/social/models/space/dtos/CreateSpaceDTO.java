package org.margin.server.social.models.space.dtos;

import org.margin.server.social.models.Visibility;

public record CreateSpaceDTO(
        String name,
        String description,
        Visibility visibility,
        Long marginId
) {}