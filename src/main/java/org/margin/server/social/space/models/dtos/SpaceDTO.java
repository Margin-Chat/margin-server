package org.margin.server.social.space.models.dtos;

import org.margin.server.social.channel.channel.ChannelDTO;
import org.margin.server.social.margin.models.dtos.MarginDTO;
import org.margin.server.social.models.Visibility;

import java.util.List;

public record SpaceDTO(
        Long spaceId,
        String spaceName,
        String spaceDescription,
        MarginDTO margin,
        Visibility visibility,
        List<ChannelDTO> channels,
        List<SpaceMemberDTO> members
) {}