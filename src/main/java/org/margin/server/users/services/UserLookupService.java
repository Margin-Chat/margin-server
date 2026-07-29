package org.margin.server.users.services;

import org.margin.server.users.api.UserLookup;
import org.margin.server.presence.PresenceService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
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
    private final PresenceService presenceService;

    public UserLookupService(UserRepository userRepository, PresenceService presenceService) {
        this.userRepository = userRepository;
        this.presenceService = presenceService;
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
    @Transactional(readOnly = true)
    public UserDTO dtoOf(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return new UserDTO(user, presenceService.isUserOnline(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> dtosOf(Collection<Long> userIds) {
        return userRepository.findAllById(userIds).stream()
                .map(u -> new UserDTO(u, presenceService.isUserOnline(u.getId())))
                .toList();
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
