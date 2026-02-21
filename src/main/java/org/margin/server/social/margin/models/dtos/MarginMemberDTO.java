package org.margin.server.social.margin.models.dtos;

import org.margin.server.social.margin.models.MarginMember;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.users.models.dtos.UserDTO;

import java.time.LocalDateTime;

public record MarginMemberDTO(
        UserDTO user,
        MarginRole role,
        LocalDateTime joinedAt
) {
    public MarginMemberDTO(MarginMember member) {
        this(new UserDTO(
                member.getUser()),
                member.getRole(),
                member.getJoinedAt());
    }
}