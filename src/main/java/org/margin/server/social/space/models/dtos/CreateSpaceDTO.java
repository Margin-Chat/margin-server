package org.margin.server.social.space.models.dtos;

import org.margin.server.social.models.Visibility;

public record CreateSpaceDTO(
        String name,
        String description,
        Visibility visibility,
        Long marginId
) {}