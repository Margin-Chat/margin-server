package org.margin.server.authentication.controllers;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.models.LoginRequest;
import org.margin.server.authentication.models.RegisterRequest;
import org.margin.server.users.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.authentication.services.AuthenticationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", request.email());

        return authenticationService.authenticateUser(
                request.email(),
                request.password());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal User user) {
        log.info("Logout attempt for user: {}", user.getId());

        authenticationService.logoutUser(user);

        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthResponse> register(
            @RequestPart("data") RegisterRequest request,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture) {
        log.info("Registration attempt for username: {}", request.username());

        try {
            authenticationService.registerUser(
                    request.username(),
                    request.email(),
                    request.password(),
                    request.encryptedPrivateKey(),
                    request.publicKey(),
                    request.salt(),
                    request.iv(),
                    profilePicture);

            AuthResponse authResponse = authenticationService.authenticateUser(
                    request.email(),
                    request.password());

            log.info("Successfully registered user {}", request.username());
            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);

        } catch (IllegalArgumentException e) {
            log.warn("Registration failed for username {}: {}", request.username(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthResponse(false, e.getMessage(), null, null, null, null, null));
        }
    }
}