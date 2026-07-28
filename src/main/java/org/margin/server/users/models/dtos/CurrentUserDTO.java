package org.margin.server.users.models.dtos;

import org.margin.server.shared.storage.PublicUrls;
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
                PublicUrls.publicUrl(user.getProfilePictureUrl()),
                user.getCreatedAt(),
                user.getLastSeenAt(),
                online
        );
    }
}
