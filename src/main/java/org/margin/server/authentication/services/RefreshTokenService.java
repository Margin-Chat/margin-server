package org.margin.server.authentication.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.entities.RefreshToken;
import org.margin.server.authentication.exceptions.InvalidRefreshTokenException;
import org.margin.server.authentication.repositories.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@Slf4j
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpirationMillis;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public record RotatedToken(Long userId, IssuedToken token) {
    }

    @Transactional
    public IssuedToken issue(Long userId) {
        return created(create(userId));
    }

    private record Created(String value, RefreshToken entity) {
    }

    private Created create(Long userId) {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expiresAt = Instant.now().plus(Duration.ofMillis(refreshExpirationMillis));

        RefreshToken saved = refreshTokenRepository.save(new RefreshToken(userId, hash(value), expiresAt));
        return new Created(value, saved);
    }

    private static IssuedToken created(Created created) {
        return new IssuedToken(created.value(), created.entity().getExpiresAt());
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotatedToken rotate(String presented) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(presented))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (existing.isRevoked()) {
            if (existing.getReplacedBy() != null) {
                log.warn("Refresh token replayed for userId {} — revoking all sessions", existing.getUserId());
                revokeAllForUser(existing.getUserId());
            }
            throw new InvalidRefreshTokenException();
        }

        if (existing.isExpired()) {
            throw new InvalidRefreshTokenException();
        }

        Created replacement = create(existing.getUserId());
        existing.setRevokedAt(Instant.now());
        existing.setReplacedBy(replacement.entity().getId());
        refreshTokenRepository.save(existing);

        return new RotatedToken(existing.getUserId(), created(replacement));
    }

    @Transactional
    public void revoke(String presented) {
        refreshTokenRepository.findByTokenHash(hash(presented))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    @Transactional
    public int deleteExpired() {
        return refreshTokenRepository.deleteExpiredBefore(Instant.now());
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
