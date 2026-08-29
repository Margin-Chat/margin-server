package org.margin.server.social.space.models.dtos;

import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.models.Visibility;

import java.util.List;

public record SpaceDTO(
        Long spaceId,
        String spaceName,
        String spaceDescription,
        Long marginId,
        Visibility visibility,
        List<ChannelDTO> channels,
        List<SpaceMemberDTO> members
) {
    public SpaceDTO withChannels(List<ChannelDTO> newChannels) {
        return new SpaceDTO(spaceId, spaceName, spaceDescription, marginId, visibility, newChannels, members);
    }
}
