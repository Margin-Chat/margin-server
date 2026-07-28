package org.margin.server.users.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.presence.PresenceService;
import org.margin.server.users.api.ProfilePictureStore;
import org.margin.server.users.events.UserDeletedEvent;
import org.margin.server.users.exceptions.UserNotFoundException;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserEncryption;
import org.margin.server.users.models.dtos.CurrentUserDTO;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.models.dtos.UserSearchResultDTO;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.repositories.projections.UserWithSharedMarginProjection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserService {
    public static final String DELETED_USER = "deleted_user_";
    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    private final PresenceService presenceService;
    private final ProfilePictureStore profilePictureStore;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       UserCacheService userCacheService,
                       PresenceService presenceService,
                       ProfilePictureStore profilePictureStore,
                       ApplicationEventPublisher applicationEventPublisher,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userCacheService = userCacheService;
        this.presenceService = presenceService;
        this.profilePictureStore = profilePictureStore;
        this.applicationEventPublisher = applicationEventPublisher;
        this.passwordEncoder = passwordEncoder;
    }

    public User getById(Long id) {
        return userCacheService.getById(id);
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase()).orElseThrow(UserNotFoundException::new);
    }

    public void savePublicPrivateKeysForUser(Long userId, String publicKey, String encryptedPrivateKey) {
        User user = getById(userId);
        user.getEncryption().setPublicKey(publicKey);
        user.getEncryption().setEncryptedPrivateKey(encryptedPrivateKey);
        userRepository.save(user);
    }

    public List<UserSearchResultDTO> searchUsersWithSharedMargins(Long searcherId, String query) {
        return userRepository.findUsersWithSharedMargins(searcherId, query).stream()
                .collect(Collectors.groupingBy(
                        UserWithSharedMarginProjection::user,
                        LinkedHashMap::new,
                        Collectors.mapping(UserWithSharedMarginProjection::marginName, Collectors.toList())
                ))
                .entrySet().stream()
                .limit(20)
                .map(entry -> new UserSearchResultDTO(
                        new UserDTO(entry.getKey(), presenceService.isUserOnline(entry.getKey().getId())),
                        entry.getValue()
                ))
                .toList();
    }

    public List<UserDTO> searchUsersByMarginId(Long searcherId, Long marginId, String query) {
        return userRepository.findUsersByMarginId(searcherId, marginId, query).stream()
                .map(this::toDTO)
                .toList();
    }

    public void evictUserCache(Long userId) {
        userCacheService.evictUserCache(userId);
    }

    public UserDTO toDTO(User user) {
        return new UserDTO(user, presenceService.isUserOnline(user.getId()));
    }

    public CurrentUserDTO toCurrentUserDTO(User user) {
        return new CurrentUserDTO(user, presenceService.isUserOnline(user.getId()));
    }

    public User updateUser(String displayName, String email, User user, MultipartFile file) {
        if (displayName != null) user.setDisplayName(displayName);
        if (email != null) user.setEmail(email);
        if (file != null && !file.isEmpty()) {
            if (user.getProfilePictureUrl() != null && !user.getProfilePictureUrl().isEmpty()) {
                profilePictureStore.delete(user.getProfilePictureUrl());
            }
            String url = profilePictureStore.save(file);
            user.setProfilePictureUrl(url);
        }

        user = userRepository.save(user);
        return user;
    }

    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword,
                               String encryptedPrivateKey, String salt, String iv) {
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));

        UserEncryption encryption = user.getEncryption();
        encryption.setEncryptedPrivateKey(encryptedPrivateKey);
        encryption.setSalt(salt);
        encryption.setIv(iv);

        userRepository.save(user);
        userCacheService.evictUserCache(user.getId());

        log.info("Password changed for user {}", user.getId());
    }

    @Transactional
    public void deleteUser(User user) {
        if (user.getProfilePictureUrl() != null) {
            profilePictureStore.delete(user.getProfilePictureUrl());
        }

        UserEncryption encryption = user.getEncryption();
        encryption.setPublicKey(null);
        encryption.setEncryptedPrivateKey(null);
        encryption.setIv(null);
        encryption.setSalt(null);
        user.setEncryption(encryption);
        user.setDisplayName("Deleted User");
        user.setEmail(DELETED_USER + user.getId() + "@margin.chat");
        user.setProfilePictureUrl(null);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        applicationEventPublisher.publishEvent(new UserDeletedEvent(user.getId()));

        log.info("User with id {} has been deleted", user.getId());
    }
}

