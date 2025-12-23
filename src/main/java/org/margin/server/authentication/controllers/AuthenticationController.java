package org.margin.server.authentication.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.authentication.services.AuthenticationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthenticationController {
    private final AuthenticationService authService;

    public AuthenticationController(AuthenticationService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        log.info("Login attempt for username: {}", request.username());

        return authService.authenticateUser(request.username(), request.password());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        log.info("Registration attempt for username: {}", request.username());

        try {
            authService.registerUser(
                    request.username(),
                    request.email(),
                    request.password(),
                    request.encryptedPrivateKey(),
                    request.publicKey(),
                    request.salt(),
                    request.iv()
            );

            AuthResponse authResponse = authService.authenticateUser(
                    request.username(),
                    request.password()
            );

            log.info("Successfully registered user {}", request.username());
            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);

        } catch (IllegalArgumentException e) {
            log.warn("Registration failed for username {}: {}", request.username(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthResponse(
                            false,
                            e.getMessage(),
                            null,
                            null,
                            null,
                            null,
                            null));
        }
    }

    public record LoginRequest(String username, String password) {
    }

    public record RegisterRequest(
            String username,
            String email,
            String password,
            String publicKey,
            String encryptedPrivateKey,
            String salt,
            String iv
    ) {
    }
}