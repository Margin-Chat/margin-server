package org.margin.server.social.space.models.dtos;

import org.margin.server.social.channel.channel.ChannelDTO;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.space.models.Space;

import java.util.List;

public record SpaceDTO(
        Long spaceId,
        String spaceName,
        String spaceDescription,
        Visibility visibility,
        List<ChannelDTO> channels,
        List<SpaceMemberDTO> members
) {
    public SpaceDTO(Space space) {
        this(
                space.getId(),
                space.getName(),
                space.getDescription(),
                space.getVisibility(),
                space.getChannels().stream().map(ChannelDTO::new).toList(),
                space.getMembers().stream().map(SpaceMemberDTO::new).toList()
        );
    }
}