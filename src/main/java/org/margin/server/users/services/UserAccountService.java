package org.margin.server.users.services;

import org.margin.server.users.api.UserAccounts;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserEncryption;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserAccountService implements UserAccounts {

    private final UserRepository userRepository;
    private final UserCacheService userCacheService;

    public UserAccountService(UserRepository userRepository, UserCacheService userCacheService) {
        this.userRepository = userRepository;
        this.userCacheService = userCacheService;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailIsTaken(String email) {
        return userRepository.findByEmail(email.toLowerCase()).isPresent();
    }

    @Override
    @Transactional
    public Long register(NewUser newUser) {
        User user = new User();
        user.setDisplayName(newUser.displayName());
        user.setEmail(newUser.email().toLowerCase());
        user.setPassword(newUser.encodedPassword());
        user.setProfilePictureUrl(newUser.profilePictureUrl());
        user.setCreatedAt(Instant.now());

        UserEncryption encryption = new UserEncryption();
        encryption.setUser(user);
        encryption.setPublicKey(newUser.publicKey());
        encryption.setEncryptedPrivateKey(newUser.encryptedPrivateKey());
        encryption.setSalt(newUser.salt());
        encryption.setIv(newUser.iv());
        user.setEncryption(encryption);

        return userRepository.save(user).getId();
    }

    @Override
    @Transactional
    public void resetCredentials(Long userId, String encodedPassword) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setPassword(encodedPassword);

        UserEncryption encryption = user.getEncryption();
        encryption.setPublicKey(null);
        encryption.setEncryptedPrivateKey(null);
        encryption.setSalt(null);
        encryption.setIv(null);

        userRepository.save(user);
        userCacheService.evictUserCache(userId);
    }

    @Override
    @Transactional
    public void updateEncryptionKeys(Long userId, String publicKey, String encryptedPrivateKey, String salt, String iv) {
        User user = userRepository.findById(userId).orElseThrow();

        UserEncryption encryption = user.getEncryption();
        encryption.setPublicKey(publicKey);
        encryption.setEncryptedPrivateKey(encryptedPrivateKey);
        encryption.setSalt(salt);
        encryption.setIv(iv);

        userRepository.save(user);
        userCacheService.evictUserCache(userId);
    }

    @Override
    public void invalidateCachedUser(Long userId) {
        userCacheService.evictUserCache(userId);
    }
}
