package org.margin.server.social.models.space.dtos;

import org.margin.server.social.models.Visibility;

public record SpaceDTO(
        Long id,
        String name,
        String description,
        Visibility visibility,
        Long marginId
) {
}