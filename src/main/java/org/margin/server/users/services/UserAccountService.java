package org.margin.server.users.services;

import org.margin.server.users.api.UserAccountCommands;
import org.margin.server.users.events.GuestPromotedEvent;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserAccountType;
import org.margin.server.users.models.UserEncryption;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserAccountService implements UserAccountCommands {
    public static final String GUEST_EMAIL_DOMAIN = "@guests.margin.invalid";

    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserRepository userRepository,
                              UserCacheService userCacheService,
                              PasswordEncoder passwordEncoder,
                              ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.userRepository = userRepository;
        this.userCacheService = userCacheService;
        this.passwordEncoder = passwordEncoder;
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

    @Override
    @Transactional
    public Long createGuest(String displayName, Instant expiresAt) {
        User user = new User();
        user.setDisplayName(displayName);
        user.setEmail("guest_" + UUID.randomUUID() + GUEST_EMAIL_DOMAIN);
        user.setPassword(passwordEncoder.encode(
                (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "")));
        user.setCreatedAt(Instant.now());
        user.setAccountType(UserAccountType.GUEST);
        user.setGuestExpiresAt(expiresAt);

        UserEncryption encryption = new UserEncryption();
        encryption.setUser(user);
        user.setEncryption(encryption);

        return userRepository.save(user).getId();
    }

    @Override
    @Transactional
    public void promoteGuest(Long userId, String email, String encodedPassword, String publicKey,
                             String encryptedPrivateKey, String salt, String iv) {
        User user = userRepository.findById(userId).orElseThrow();
        if (!user.isGuest()) {
            throw new IllegalStateException("User " + userId + " is not a guest");
        }

        user.setEmail(email.toLowerCase());
        user.setPassword(encodedPassword);
        user.setAccountType(UserAccountType.FULL);
        user.setGuestExpiresAt(null);

        UserEncryption encryption = user.getEncryption();
        encryption.setPublicKey(publicKey);
        encryption.setEncryptedPrivateKey(encryptedPrivateKey);
        encryption.setSalt(salt);
        encryption.setIv(iv);

        userRepository.save(user);
        userCacheService.evictUserCache(userId);
        eventPublisher.publishEvent(
                new GuestPromotedEvent(userId, user.getEmail(), user.getDisplayName()));
    }
}
