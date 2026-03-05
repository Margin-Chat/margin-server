package org.margin.server.social.margin.models.dtos;

public record UpdateMarginDTO(Long marginId,
                              String marginName,
                              String description) {
}
