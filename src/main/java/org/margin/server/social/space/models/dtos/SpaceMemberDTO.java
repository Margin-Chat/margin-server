package org.margin.server.social.space.models.dtos;

import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.users.models.dtos.UserDTO;

import java.time.LocalDateTime;

public record SpaceMemberDTO(
        UserDTO user,
        Long spaceId,
        SpaceRole role,
        LocalDateTime joinedAt
) {
    public SpaceMemberDTO(SpaceMember member, boolean isOnline) {
        this(new UserDTO(member.getUser(), isOnline),
                member.getSpace().getId(),
                member.getRole(),
                member.getJoinedAt());
    }
}