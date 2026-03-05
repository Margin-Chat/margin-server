package org.margin.server.authentication.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.margin.server.users.services.UserService;
import org.margin.server.users.models.User;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class JwtService {
    private final UserService userService;
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}") // 24 hours
    private Long expiration;

    public JwtService(UserService userService) {
        this.userService = userService;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        return createToken(claims, email);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, String email) {
        final String tokenEmail = extractEmail(token);
        return (tokenEmail.equals(email)) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    public Optional<User> extractAndValidateJwtTokenFromWebSocket(String uri) {
        try {
            URI fullUri = new URI(uri);
            String query = fullUri.getQuery();
            String token = extractTokenFromQuery(query);

            if (token == null || token.isEmpty()) {
                log.warn("Missing token in WebSocket connection");
                return Optional.empty();
            }

            String email = extractEmail(token);

            if (!isTokenValid(token, email)) {
                log.warn("Invalid or expired JWT token");
                return Optional.empty();
            }

            User user = userService.getById(
                    extractAllClaims(token).get("userId", Long.class)
            );
            return Optional.of(user);
        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String extractTokenFromQuery(String query) {
        if (query == null) return null;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
}
