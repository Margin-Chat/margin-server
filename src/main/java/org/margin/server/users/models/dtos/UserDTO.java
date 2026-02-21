package org.margin.server.users.models.dtos;

import org.margin.server.users.models.User;

import java.time.LocalDateTime;

public record UserDTO(
        Long id,
        String username,
        String email,
        String profilePictureUrl,
        LocalDateTime createdAt
) {
    public UserDTO(User user) {
        this(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getProfilePictureUrl(),
                user.getCreatedAt()
        );
    }
}
