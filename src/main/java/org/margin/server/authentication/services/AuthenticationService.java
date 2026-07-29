package org.margin.server.authentication.services;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.authentication.entities.UserSecurity;
import org.margin.server.authentication.events.UserSessionsRevokedEvent;
import org.margin.server.authentication.exceptions.RegistrationException;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.email.EmailService;
import org.margin.server.users.api.ProfilePictureStore;
import org.margin.server.users.api.UserAccounts;
import org.margin.server.users.api.UserLookup;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
    private final UserLookup userLookup;
    private final UserAccounts userAccounts;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ProfilePictureStore profilePictureStore;
    private final ApplicationEventPublisher eventPublisher;
    private final ActivationKeyService activationKeyService;
    private final EmailService emailService;
    private final UserSecurityService userSecurityService;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            UserLookup userLookup,
            UserAccounts userAccounts,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            ProfilePictureStore profilePictureStore,
            ApplicationEventPublisher eventPublisher,
            ActivationKeyService activationKeyService,
            EmailService emailService,
            UserSecurityService userSecurityService) {
        this.authenticationManager = authenticationManager;
        this.userLookup = userLookup;
        this.userAccounts = userAccounts;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.profilePictureStore = profilePictureStore;
        this.eventPublisher = eventPublisher;
        this.activationKeyService = activationKeyService;
        this.emailService = emailService;
        this.userSecurityService = userSecurityService;
    }

    public AuthResponse authenticateUser(String email, String password) {
        String normalisedEmail = email.toLowerCase();
        log.info("Login attempt for email {}", normalisedEmail);
        try {
            User user = userLookup.findByEmail(normalisedEmail)
                    .orElseThrow(() -> new BadCredentialsException("user not found"));

            if (!activationKeyService.isUserActivated(user.getId())) {
                log.warn("Login blocked — userId {} not yet activated", user.getId());
                return new AuthResponse(false, "User is not yet activated",
                        null, null, null, null, null);
            }

            UserSecurity security = userSecurityService.get(user.getId());
            if (isAccountLocked(security)) {
                log.warn("Login blocked — userId {} locked until {}", user.getId(), security.getAccountLockedUntil());
                throw new BadCredentialsException("account locked");
            }

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalisedEmail, password)
            );

            resetFailedAttempts(security);
            String token = jwtService.generateToken(email, user.getId(), security.getTokenVersion());
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
        if (userAccounts.emailIsTaken(email)) {
            throw new IllegalArgumentException("Email already in use");
        }

        String profilePictureUrl = null;
        if (profilePicture != null && !profilePicture.isEmpty()) {
            profilePictureUrl = profilePictureStore.save(profilePicture);
        }

        Long userId = userAccounts.register(new UserAccounts.NewUser(
                displayName, email, passwordEncoder.encode(password), profilePictureUrl,
                publicKey, privateKey, salt, iv));

        UserSecurity security = new UserSecurity(userId);
        security.setFailedLoginAttempts(0);
        userSecurityService.save(security);

        ActivationKey activationKey = activationKeyService.generateActivationKey(userId);

        if (!requireEmailActivation) {
            activationKeyService.findAndConsumeActivationKey(activationKey.getToken());
            log.info("Email activation disabled — user {} auto-activated", email);
            return activationKey;
        }

        String registrationContent = emailService.buildRegistrationMail(displayName, activationKey.getToken());
        try {
            emailService.sendEmail(email, "Email activation for margin", registrationContent);
        } catch (MessagingException _) {
            throw new RegistrationException("Failed to send activation email for margin");
        }

        log.info("Email registration sent for user {}", email);

        return activationKey;
    }

    private boolean isAccountLocked(UserSecurity security) {
        if (security.getAccountLockedUntil() == null) {
            return false;
        }
        return security.getAccountLockedUntil().isAfter(Instant.now());
    }

    private void handleFailedLogin(String email) {
        userLookup.findByEmail(email.toLowerCase()).ifPresent(user -> {
            UserSecurity security = userSecurityService.get(user.getId());
            security.setFailedLoginAttempts(security.getFailedLoginAttempts() + 1);
            security.setLastFailedLoginAttempt(Instant.now());

            if (security.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                security.setAccountLockedUntil(Instant.now().plusSeconds(LOCK_DURATION_SECONDS));
                log.warn("Account locked for user {} until {}",
                        user.getDisplayName(), security.getAccountLockedUntil());
            }

            userSecurityService.save(security);
        });
    }

    private void resetFailedAttempts(UserSecurity security) {
        if (security.getFailedLoginAttempts() > 0) {
            security.setFailedLoginAttempts(0);
            security.setLastFailedLoginAttempt(null);
            security.setAccountLockedUntil(null);
            userSecurityService.save(security);
        }
    }

    @Transactional
    public void logoutUser(User user) {
        userSecurityService.bumpTokenVersion(user.getId());
        userAccounts.invalidateCachedUser(user.getId());
        eventPublisher.publishEvent(new UserSessionsRevokedEvent(user.getId()));
    }

    @Transactional
    public void updateEncryptionKeys(User user, String publicKey, String encryptedPrivateKey, String salt, String iv) {
        userAccounts.updateEncryptionKeys(user.getId(), publicKey, encryptedPrivateKey, salt, iv);
    }
}