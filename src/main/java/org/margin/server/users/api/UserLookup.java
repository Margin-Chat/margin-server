package org.margin.server.users.api;

import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserLookup {

    record UserContact(Long id, String displayName, String email) {
    }

    Optional<User> findById(Long userId);

    Optional<User> findByEmail(String email);


    UserContact contactOf(Long userId);

    UserDTO dtoOf(Long userId);

    UserSummary summaryOf(Long userId);

    Optional<Long> idByEmail(String email);

    Map<Long, String> publicKeysOf(Collection<Long> userIds);

    List<UserDTO> dtosOf(Collection<Long> userIds);

    void markLastSeen(Long userId, Instant lastSeenAt);

    boolean isGuest(Long userId);
}
