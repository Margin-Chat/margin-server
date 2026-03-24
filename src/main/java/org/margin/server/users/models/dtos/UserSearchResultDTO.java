package org.margin.server.users.models.dtos;

import java.util.List;

public record UserSearchResultDTO(
        UserDTO user,
        List<String> sharedMargins
) {
}
