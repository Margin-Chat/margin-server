package org.margin.server.users.services;

import org.margin.server.social.margin.events.RemoveUserFromMarginEvent;
import org.margin.server.storage.StorageService;
import org.margin.server.users.exceptions.UserNotFoundException;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserEncryption;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.models.dtos.UserSearchResultDTO;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.repositories.projections.UserWithSharedMarginProjection;
import org.margin.server.websocket.connection.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    public static final String DELETED_USER = "deleted_user_";
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    private final ConnectionManager connectionManager;
    private final StorageService storageService;
    private final ApplicationEventPublisher applicationEventPublisher;


    public UserService(UserRepository userRepository,
                       UserCacheService userCacheService,
                       ConnectionManager connectionManager,
                       StorageService storageService,
                       ApplicationEventPublisher applicationEventPublisher) {
        this.userRepository = userRepository;
        this.userCacheService = userCacheService;
        this.connectionManager = connectionManager;
        this.storageService = storageService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public User getById(Long id) {
        return userCacheService.getById(id);
    }

    public User getByHandle(String handle) {
        return userRepository.findByHandle(handle).orElseThrow(UserNotFoundException::new);
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
                        new UserDTO(entry.getKey(), connectionManager.isUserOnline(entry.getKey().getId())),
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
        return new UserDTO(user, connectionManager.isUserOnline(user.getId()));
    }

    public User updateUser(String displayName, String email, User user, MultipartFile file) {
        if (displayName != null) user.setDisplayName(displayName);
        if (email != null) user.setEmail(email);
        if (file != null && !file.isEmpty()) {
            if (!user.getProfilePictureUrl().isEmpty()) {
                storageService.deleteProfilePicture(user.getProfilePictureUrl());
            }
            String url = storageService.saveProfilePicture(file);
            user.setProfilePictureUrl(url);
        }

        user = userRepository.save(user);
        return user;
    }

    @Transactional
    public void deleteUser(List<Long> marginIds, User user) {
        if (user.getProfilePictureUrl() != null) {
            storageService.deleteProfilePicture(user.getProfilePictureUrl());
        }

        marginIds.forEach(marginId -> {
            var removeUserFromMarginEvent = new RemoveUserFromMarginEvent(marginId, user.getId());
            applicationEventPublisher.publishEvent(removeUserFromMarginEvent);
        });

        UserEncryption encryption = user.getEncryption();
        encryption.setPublicKey(null);
        encryption.setEncryptedPrivateKey(null);
        encryption.setIv(null);
        encryption.setSalt(null);
        user.setEncryption(encryption);
        user.setHandle(DELETED_USER + user.getId());
        user.setDisplayName("Deleted User");
        user.setEmail(DELETED_USER + user.getId() + "@margin.chat");
        user.setProfilePictureUrl(null);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        log.info("User with id {} has been deleted", user.getId());
    }
}

