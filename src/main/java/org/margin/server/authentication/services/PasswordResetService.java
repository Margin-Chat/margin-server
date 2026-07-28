package org.margin.server.authentication.services;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.entities.PasswordResetToken;
import org.margin.server.authentication.repositories.PasswordResetTokenRepository;
import org.margin.server.email.EmailService;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.services.UserCacheService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class PasswordResetService {

    private static final int EXPIRY_SECONDS = 3600;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final UserCacheService userCacheService;
    private final UserSecurityService userSecurityService;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                UserCacheService userCacheService,
                                UserSecurityService userSecurityService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.userCacheService = userCacheService;
        this.userSecurityService = userSecurityService;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String normalised = email.toLowerCase();
        log.info("Password reset requested for email {}", normalised);
        userRepository.findByEmail(normalised).ifPresent(user -> {
            tokenRepository.deleteByUser(user);

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(UUID.randomUUID().toString());
            resetToken.setExpiresAt(Instant.now().plusSeconds(EXPIRY_SECONDS));
            tokenRepository.save(resetToken);

            String body = emailService.buildPasswordResetMail(user.getDisplayName(), resetToken.getToken());
            try {
                emailService.sendEmail(user.getEmail(), "Reset your margin password", body);
                log.info("Password reset email sent to userId={}", user.getId());
            } catch (MessagingException e) {
                log.error("Failed to send password reset email to userId={}", user.getId(), e);
            }
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        log.info("Password reset attempt for token prefix {}", token.length() > 8 ? token.substring(0, 8) : "short");

        PasswordResetToken resetToken = tokenRepository.findByToken(token).orElse(null);
        if (resetToken == null) {
            log.warn("Password reset failed — token not found");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        if (resetToken.isExpired()) {
            log.warn("Password reset failed — token expired at {} userId {}", resetToken.getExpiresAt(), resetToken.getUser().getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token has expired");
        }

        if (resetToken.isUsed()) {
            log.warn("Password reset failed — token already used at {} userId {}", resetToken.getUsedAt(), resetToken.getUser().getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token has already been used");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userSecurityService.bumpTokenVersion(user.getId());
        user.getEncryption().setPublicKey(null);
        user.getEncryption().setEncryptedPrivateKey(null);
        user.getEncryption().setSalt(null);
        user.getEncryption().setIv(null);
        log.info("Encryption keys cleared for userId {} — will be regenerated on next login", user.getId());

        userRepository.save(user);
        userCacheService.evictUserCache(user.getId());

        resetToken.setUsedAt(Instant.now());
        tokenRepository.save(resetToken);

        log.info("Password reset successfully for userId {}", user.getId());
    }
}