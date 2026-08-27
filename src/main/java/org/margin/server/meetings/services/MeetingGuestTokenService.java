package org.margin.server.meetings.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.meetings.security.MeetingGuestPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class MeetingGuestTokenService {

    public static final String TOKEN_TYPE = "guest";
    private static final long MAX_LIFETIME_MILLIS = 12 * 60 * 60 * 1000L;

    @Value("${margin.meetings.guest-token-secret}")
    private String tokenSecret;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(tokenSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, Long meetingId, String meetingCode, String displayName, Instant meetingExpiresAt) {
        long cap = System.currentTimeMillis() + MAX_LIFETIME_MILLIS;
        long expiry = Math.min(cap, meetingExpiresAt.toEpochMilli());

        return Jwts.builder()
                .claims(Map.of(
                        "typ", TOKEN_TYPE,
                        "uid", userId,
                        "mid", meetingId,
                        "dn", displayName))
                .subject(meetingCode)
                .issuedAt(new Date())
                .expiration(new Date(expiry))
                .signWith(getSigningKey())
                .compact();
    }

    public Optional<MeetingGuestPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TOKEN_TYPE.equals(claims.get("typ", String.class))) {
                return Optional.empty();
            }

            return Optional.of(new MeetingGuestPrincipal(
                    claims.get("uid", Long.class),
                    claims.get("mid", Long.class),
                    claims.getSubject(),
                    claims.get("dn", String.class)));
        } catch (Exception e) {
            log.debug("Rejected guest token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
