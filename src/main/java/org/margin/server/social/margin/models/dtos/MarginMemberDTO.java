package org.margin.server.social.margin.models.dtos;

import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;

public record MarginMemberDTO(
        UserDTO user,
        MarginRole role,
        Instant joinedAt
) {
}