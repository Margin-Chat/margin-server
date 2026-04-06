package org.margin.server.users.models.dtos;

import org.margin.server.users.models.User;

import java.time.Instant;

public record UserDTO(
        Long id,
        String handle,
        String displayName,
        String email,
        String profilePictureUrl,
        Instant createdAt,
        boolean isOnline
) {
    public UserDTO(User user, boolean online) {
        this(
                user.getId(),
                user.getHandle(),
                user.getDisplayName(),
                user.getEmail(),
                user.getProfilePictureUrl(),
                user.getCreatedAt(),
                online
        );
    }
}
