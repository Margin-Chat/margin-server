package org.margin.server.social.space.models.dtos;

import org.margin.server.social.space.models.SpaceMember;
import org.margin.server.social.space.models.SpaceRole;
import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;

public record SpaceMemberDTO(
        UserDTO user,
        Long spaceId,
        SpaceRole role,
        Instant joinedAt
) {
    public SpaceMemberDTO(SpaceMember member, UserDTO user) {
        this(user,
                member.getSpace().getId(),
                member.getRole(),
                member.getJoinedAt());
    }
}