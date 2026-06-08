package org.margin.server.authentication.services;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.authentication.exceptions.RegistrationException;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.email.EmailService;
import org.margin.server.storage.services.StorageService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserEncryption;
import org.margin.server.users.models.UserSecurity;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.websocket.connection.ConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@Service
@Slf4j
public class AuthenticationService {
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int LOCK_DURATION_SECONDS = 30;

    @Value("${margin.mail.require-email-activation:true}")
    private boolean requireEmailActivation;

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final ConnectionManager connectionManager;
    private final ActivationKeyService activationKeyService;
    private final EmailService emailService;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            StorageService storageService,
            ConnectionManager connectionManager,
            ActivationKeyService activationKeyService,
            EmailService emailService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
        this.connectionManager = connectionManager;
        this.activationKeyService = activationKeyService;
        this.emailService = emailService;
    }

    public AuthResponse authenticateUser(String email, String password) {
        String normalisedEmail = email.toLowerCase();
        log.info("Login attempt for email {}", normalisedEmail);
        try {
            User user = userRepository.findByEmail(normalisedEmail)
                    .orElseThrow(() -> new BadCredentialsException("user not found"));

            if (!activationKeyService.isUserActivated(user)) {
                log.warn("Login blocked — userId {} not yet activated", user.getId());
                return new AuthResponse(false, "User is not yet activated",
                        null, null, null, null, null);
            }

            if (isAccountLocked(user)) {
                log.warn("Login blocked — userId {} locked until {}", user.getId(), user.getSecurity().getAccountLockedUntil());
                throw new BadCredentialsException("account locked");
            }

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalisedEmail, password)
            );

            resetFailedAttempts(user);
            String token = jwtService.generateToken(email, user.getId());
            log.info("Login successful for userId {}", user.getId());

            return new AuthResponse(
                    true,
                    "Login successful",
                    token,
                    user.getEncryption().getPublicKey(),
                    user.getEncryption().getEncryptedPrivateKey(),
                    user.getEncryption().getSalt(),
                    user.getEncryption().getIv());

        } catch (BadCredentialsException e) {
            log.warn("Login failed for email {} reason {}", normalisedEmail, e.getMessage());
            handleFailedLogin(normalisedEmail);
            return new AuthResponse(
                    false,
                    "Invalid credentials",
                    null, null, null, null, null);
        }
    }

    @Transactional
    public ActivationKey registerUser(String displayName,
                                      String email,
                                      String password,
                                      String privateKey,
                                      String publicKey,
                                      String salt,
                                      String iv,
                                      MultipartFile profilePicture) {
        if (userRepository.findByEmail(email.toLowerCase()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        String profilePictureUrl = null;
        if (profilePicture != null && !profilePicture.isEmpty()) {
            profilePictureUrl = storageService.saveProfilePicture(profilePicture);
        }

        User user = new User();
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

        userRepository.save(user);

        ActivationKey activationKey = activationKeyService.generateActivationKey(user);

        if (!requireEmailActivation) {
            activationKeyService.findAndConsumeActivationKey(activationKey.getToken());
            log.info("Email activation disabled — user {} auto-activated", user.getEmail());
            return activationKey;
        }

        String registrationContent = emailService.buildRegistrationMail(user.getDisplayName(), activationKey.getToken());
        try {
            emailService.sendEmail(user.getEmail(), "Email activation for margin", registrationContent);
        } catch (MessagingException _) {
            throw new RegistrationException("Failed to send activation email for margin");
        }

        log.info("Email registration sent for user {}", user.getEmail());

        return activationKey;
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

    @Transactional
    public void updateEncryptionKeys(User user, String publicKey, String encryptedPrivateKey, String salt, String iv) {
        user.getEncryption().setPublicKey(publicKey);
        user.getEncryption().setEncryptedPrivateKey(encryptedPrivateKey);
        user.getEncryption().setSalt(salt);
        user.getEncryption().setIv(iv);
        userRepository.save(user);
    }
}