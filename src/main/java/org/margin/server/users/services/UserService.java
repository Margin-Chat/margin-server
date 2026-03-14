package org.margin.server.users.services;

import org.margin.server.storage.StorageService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.websocket.connection.ConnectionManager;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    private final ConnectionManager connectionManager;
    private final StorageService storageService;


    public UserService(UserRepository userRepository, UserCacheService userCacheService, ConnectionManager connectionManager, StorageService storageService) {
        this.userRepository = userRepository;
        this.userCacheService = userCacheService;
        this.connectionManager = connectionManager;
        this.storageService = storageService;

    }

    public User getById(Long id) {
        return userCacheService.getById(id);
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void savePublicPrivateKeysForUser(Long userId, String publicKey, String encryptedPrivateKey) {
        User user = getById(userId);
        user.getEncryption().setPublicKey(publicKey);
        user.getEncryption().setEncryptedPrivateKey(encryptedPrivateKey);
        userRepository.save(user);
    }

    public List<UserDTO> searchForUser(String query) {
        return userRepository.findTop20ByUsernameContainingIgnoreCase(query).stream()
                .map(user -> new UserDTO(user, connectionManager.isUserOnline(user.getId())))
                .collect(Collectors.toList());
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
            String url = storageService.saveProfilePicture(file);
            user.setProfilePictureUrl(url);
        }

        user = userRepository.save(user);
        return user;
    }
}

