package org.margin.server.users.models.dtos;

import org.margin.server.users.models.User;

import java.time.LocalDateTime;

public record UserDTO(
        Long id,
        String username,
        String displayName,
        String email,
        String profilePictureUrl,
        LocalDateTime createdAt,
        boolean isOnline
) {
    public UserDTO(User user, boolean online) {
        this(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getProfilePictureUrl(),
                user.getCreatedAt(),
                online
        );
    }
}
