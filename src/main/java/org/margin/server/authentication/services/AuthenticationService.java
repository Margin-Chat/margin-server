package org.margin.server.authentication.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.entities.BetaKey;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.authentication.repositories.BetaKeyRepository;
import org.margin.server.storage.StorageService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserEncryption;
import org.margin.server.users.models.UserSecurity;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.websocket.connection.ConnectionManager;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@Service
@Slf4j
public class AuthenticationService {
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int LOCK_DURATION_SECONDS = 30;

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final ConnectionManager connectionManager;
    private final BetaKeyRepository betaKeyRepository;
    private final ActivationKeyService activationKeyService;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            StorageService storageService,
            ConnectionManager connectionManager, BetaKeyRepository betaKeyRepository, ActivationKeyService activationKeyService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
        this.connectionManager = connectionManager;
        this.betaKeyRepository = betaKeyRepository;
        this.activationKeyService = activationKeyService;
    }

    public AuthResponse authenticateUser(String email, String password) {
        try {
            User user = userRepository.findByEmail(email.toLowerCase())
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

            if (!activationKeyService.isUserActivated(user)) {
                log.info("Login blocked — user {} is not yet activated", user.getId());
                return new AuthResponse(false, "User is not yet activated",
                        null, null, null, null, null);
            }

            if (isAccountLocked(user)) {
                throw new BadCredentialsException("Account is locked until " + user.getSecurity().getAccountLockedUntil());
            }

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email.toLowerCase(), password)
            );

            resetFailedAttempts(user);

            String token = jwtService.generateToken(email, user.getId());

            log.info("User {} authenticated successfully", user.getDisplayName());

            return new AuthResponse(
                    true,
                    "Login successful",
                    token,
                    user.getEncryption().getPublicKey(),
                    user.getEncryption().getEncryptedPrivateKey(),
                    user.getEncryption().getSalt(),
                    user.getEncryption().getIv());

        } catch (BadCredentialsException _) {
            handleFailedLogin(email);
            log.info("Authentication failed for email: {}", email);
            return new AuthResponse(
                    false,
                    "Invalid credentials",
                    null, null, null, null, null);
        }
    }

    public User registerUser(String handle,
                             String displayName,
                             String email,
                             String password,
                             String privateKey,
                             String publicKey,
                             String salt,
                             String iv,
                             String betaKey,
                             MultipartFile profilePicture) {
        BetaKey key = betaKeyRepository.findByKey(betaKey)
                .orElseThrow(() -> new IllegalArgumentException("Invalid beta key"));

        if (key.isUsed()) {
            throw new IllegalArgumentException("This beta key has already been used");
        }

        if (userRepository.findByEmail(email.toLowerCase()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        if (userRepository.findByHandle(handle.toLowerCase()).isPresent()) {
            throw new IllegalArgumentException("Handle already exists");
        }

        String profilePictureUrl = null;
        if (profilePicture != null && !profilePicture.isEmpty()) {
            profilePictureUrl = storageService.saveProfilePicture(profilePicture);
        }

        User user = new User();
        user.setHandle(handle.toLowerCase());
        user.setDisplayName(displayName);
        user.setEmail(email.toLowerCase());
        user.setPassword(passwordEncoder.encode(password));
        user.setProfilePictureUrl(profilePictureUrl);
        user.setCreatedAt(Instant.now());

        UserEncryption encryption = new UserEncryption();
        encryption.setUser(user);
        encryption.setSalt(salt);
        encryption.setIv(iv);
        encryption.setPublicKey(publicKey);
        encryption.setEncryptedPrivateKey(privateKey);
        user.setEncryption(encryption);

        UserSecurity security = new UserSecurity();
        security.setUser(user);
        security.setFailedLoginAttempts(0);
        user.setSecurity(security);

        User savedUser = userRepository.save(user);

        key.setUsedBy(savedUser);
        key.setUsedAt(Instant.now());
        betaKeyRepository.save(key);

        return user;
    }

    private boolean isAccountLocked(User user) {
        if (user.getSecurity().getAccountLockedUntil() == null) {
            return false;
        }
        return user.getSecurity().getAccountLockedUntil().isAfter(Instant.now());
    }

    private void handleFailedLogin(String email) {
        userRepository.findByEmail(email.toLowerCase()).ifPresent(user -> {
            user.getSecurity().setFailedLoginAttempts(user.getSecurity().getFailedLoginAttempts() + 1);
            user.getSecurity().setLastFailedLoginAttempt(Instant.now());

            if (user.getSecurity().getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                user.getSecurity().setAccountLockedUntil(Instant.now().plusSeconds(LOCK_DURATION_SECONDS));
                log.warn("Account locked for user {} until {}",
                        user.getDisplayName(), user.getSecurity().getAccountLockedUntil());
            }

            userRepository.save(user);
        });
    }

    private void resetFailedAttempts(User user) {
        if (user.getSecurity().getFailedLoginAttempts() > 0) {
            user.getSecurity().setFailedLoginAttempts(0);
            user.getSecurity().setLastFailedLoginAttempt(null);
            user.getSecurity().setAccountLockedUntil(null);
            userRepository.save(user);
        }
    }

    public void logoutUser(User user) {
        connectionManager.closeAllSessions(user.getId());
    }
}