package org.margin.server.social.margin.models.dtos;

import org.margin.server.social.models.Visibility;
import org.margin.server.social.space.models.dtos.SpaceDTO;

import java.util.List;

public record MarginDTO(
        Long marginId,
        String marginName,
        String marginDescription,
        Visibility visibility,
        String marginIconUrl,
        List<MarginMemberDTO> members,
        List<SpaceDTO> spaces
) {}