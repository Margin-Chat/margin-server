package org.margin.server.users.api;

import org.margin.server.users.models.User;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserLookup {

    record UserContact(Long id, String displayName, String email) {
    }

    Optional<User> findById(Long userId);

    Optional<User> findByEmail(String email);

    List<User> findAllById(Collection<Long> userIds);

    UserContact contactOf(Long userId);

    void markLastSeen(Long userId, Instant lastSeenAt);
}
