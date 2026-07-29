package org.margin.server.social.margin.service;

import org.margin.server.presence.PresenceService;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.models.ChannelDTO;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.models.dtos.MarginDTO;
import org.margin.server.social.margin.models.dtos.MarginMemberDTO;
import org.margin.server.social.space.models.Space;
import org.margin.server.social.space.models.dtos.SpaceDTO;
import org.margin.server.social.space.models.dtos.SpaceMemberDTO;
import org.margin.server.shared.storage.PublicUrls;
import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MarginMapper {
    private final PresenceService presenceService;

    public MarginMapper(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    public MarginDTO marginToDto(Margin margin) {
        List<MarginMemberDTO> members = margin.getMembers().stream()
                .map(m -> new MarginMemberDTO(
                        new UserDTO(m.getUser(), presenceService.isUserOnline(m.getUser().getId())),
                        m.getRole(),
                        m.getJoinedAt()
                ))
                .toList();

        List<SpaceDTO> spaces = margin.getSpaces().stream()
                .map(this::spaceToDto)
                .toList();

        return new MarginDTO(margin.getId(), margin.getName(), margin.getDescription(),
                margin.getVisibility(), PublicUrls.publicUrl(margin.getIconUrl()), members, spaces);
    }

    public SpaceDTO spaceToDto(Space space) {
        List<SpaceMemberDTO> members = space.getMembers() != null
                ? space.getMembers().stream()
                .map(m -> new SpaceMemberDTO(
                        new UserDTO(
                                m.getUser(),
                                presenceService.isUserOnline(m.getUser().getId())),
                        m.getSpace().getId(),
                        m.getRole(),
                        m.getJoinedAt()
                ))
                .toList()
                : List.of();

        List<ChannelDTO> channels = space.getChannels() != null
                ? space.getChannels().stream().map(this::channelToDto).toList()
                : List.of();

        return new SpaceDTO(
                space.getId(),
                space.getName(),
                space.getDescription(),
                space.getMargin().getId(),
                space.getVisibility(),
                channels,
                members);
    }

    public ChannelDTO channelToDto(Channel channel) {
        return new ChannelDTO(
                channel.getId(),
                channel.getName(),
                channel.getDescription(),
                channel.getConversation().getId(),
                channel.getChannelType(),
                channel.getSpace().getId()
        );
    }
}
