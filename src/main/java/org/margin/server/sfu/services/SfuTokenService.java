package org.margin.server.sfu.services;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@NamedInterface("api")
@Service
public class SfuTokenService {
    @Value("${sfu.token-secret}")
    private String tokenSecret;

    @Value("${sfu.token-expiration}")
    private Long tokenExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(tokenSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateRoomToken(Long userId, String roomId, String displayName) {
        return Jwts.builder()
                .claims(Map.of("userId", userId, "roomId", roomId, "displayName", displayName))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + tokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }
}