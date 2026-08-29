package org.margin.server.integrationtest.utils;

import org.margin.server.authentication.entities.PasswordResetToken;
import org.margin.server.authentication.repositories.PasswordResetTokenRepository;
import org.margin.server.authentication.services.PasswordResetService;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class PasswordResetTestUtils {

    private static PasswordResetService passwordResetService;
    private static PasswordResetTokenRepository tokenRepository;

    @Autowired
    public PasswordResetTestUtils(PasswordResetService passwordResetService,
                                  PasswordResetTokenRepository tokenRepository) {
        PasswordResetTestUtils.passwordResetService = passwordResetService;
        PasswordResetTestUtils.tokenRepository = tokenRepository;
    }

    public static void requestReset(String email) {
        passwordResetService.requestPasswordReset(email);
    }

    public static void resetPassword(String token, String newPassword) {
        passwordResetService.resetPassword(token, newPassword);
    }

    public static PasswordResetToken getTokenForUser(User user) {
        return tokenRepository.findAll().stream()
                .filter(t -> t.getUserId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
    }

    public static PasswordResetToken saveExpiredToken(User user) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(Instant.now().minusSeconds(60));
        return tokenRepository.save(token);
    }

    public static PasswordResetToken saveUsedToken(User user) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setUsedAt(Instant.now().minusSeconds(30));
        return tokenRepository.save(token);
    }
}
