package org.margin.server.sfu.services;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class SfuTokenService {

    @Value("${sfu.token-secret}")
    private String tokenSecret;

    @Value("${sfu.token-expiration}")
    private Long tokenExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(tokenSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateRoomToken(Long userId, String roomId) {
        return Jwts.builder()
                .setClaims(Map.of("userId", userId, "roomId", roomId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + tokenExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
}