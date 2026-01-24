package org.margin.server.social.models.space;

import org.margin.server.social.models.space.enums.SpaceVisibility;

public record SpaceDTO(String name,
                       String description,
                       SpaceVisibility visibility) {
}

