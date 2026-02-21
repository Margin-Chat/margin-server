package org.margin.server.social.margin.models.dtos;

import org.margin.server.social.margin.models.Margin;
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
) {
    public MarginDTO(Margin margin) {
        this(
                margin.getId(),
                margin.getName(),
                margin.getDescription(),
                margin.getVisibility(),
                margin.getIconUrl(),
                margin.getMembers().stream().map(MarginMemberDTO::new).toList(),
                margin.getSpaces().stream().map(SpaceDTO::new).toList()
        );
    }
}