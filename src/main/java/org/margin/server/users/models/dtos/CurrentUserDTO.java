package org.margin.server.users.models.dtos;

import org.margin.server.storage.services.StorageUrls;
import org.margin.server.users.models.User;

import java.time.Instant;

public record CurrentUserDTO(
        Long id,
        String displayName,
        String email,
        String profilePictureUrl,
        Instant createdAt,
        Instant lastSeenAt,
        boolean isOnline
) {
    public CurrentUserDTO(User user, boolean online) {
        this(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                StorageUrls.publicUrl(user.getProfilePictureUrl()),
                user.getCreatedAt(),
                user.getLastSeenAt(),
                online
        );
    }
}
