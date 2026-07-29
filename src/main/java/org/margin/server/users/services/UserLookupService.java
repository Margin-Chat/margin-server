package org.margin.server.users.services;

import org.margin.server.users.api.UserLookup;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class UserLookupService implements UserLookup {

    private final UserRepository userRepository;

    public UserLookupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public List<User> findAllById(Collection<Long> userIds) {
        return userRepository.findAllById(userIds);
    }

    @Override
    @Transactional(readOnly = true)
    public UserContact contactOf(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return new UserContact(user.getId(), user.getDisplayName(), user.getEmail());
    }

    @Override
    @Transactional
    public void markLastSeen(Long userId, Instant lastSeenAt) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastSeenAt(lastSeenAt);
            userRepository.save(user);
        });
    }
}
