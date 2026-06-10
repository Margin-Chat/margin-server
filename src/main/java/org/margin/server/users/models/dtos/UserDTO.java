package org.margin.server.users.models.dtos;

import org.margin.server.storage.services.StorageUrls;
import org.margin.server.users.models.User;

import java.time.Instant;

public record UserDTO(
        Long id,
        String displayName,
        String profilePictureUrl,
        Instant createdAt,
        Instant lastSeenAt,
        boolean isOnline
) {
    public UserDTO(User user, boolean online) {
        this(
                user.getId(),
                user.getDisplayName(),
                StorageUrls.publicUrl(user.getProfilePictureUrl()),
                user.getCreatedAt(),
                user.getLastSeenAt(),
                online
        );
    }
}
