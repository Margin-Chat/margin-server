package org.margin.server.users.api;

import org.margin.server.users.models.User;

import java.time.Instant;
import java.util.Optional;

public interface UserLookup {

    Optional<User> findById(Long userId);

    Optional<User> findByEmail(String email);

    void markLastSeen(Long userId, Instant lastSeenAt);
}
