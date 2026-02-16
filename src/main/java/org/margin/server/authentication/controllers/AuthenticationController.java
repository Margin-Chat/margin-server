package org.margin.server.authentication.controllers;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.models.LoginRequest;
import org.margin.server.authentication.models.LogoutRequest;
import org.margin.server.authentication.models.RegisterRequest;
import org.margin.server.connection.ConnectionManager;
import org.margin.server.users.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.authentication.services.AuthenticationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthenticationController {

    private final AuthenticationService authService;
    private final ConnectionManager connectionManager;
    private final UserService userService;

    public AuthenticationController(AuthenticationService authService, ConnectionManager connectionManager, UserService userService) {
        this.authService = authService;
        this.connectionManager = connectionManager;
        this.userService = userService;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        log.info("Login attempt for username: {}", request.username());

        return authService.authenticateUser(request.username(), request.password());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequest logoutRequest) {
        log.info("Logout attempt for user: {}", logoutRequest.userId());

        var connection = connectionManager.getConnection(logoutRequest.userId());
        if (connection != null) {
            connection.close();
            connectionManager.removeConnection(userService.getById(logoutRequest.userId()));
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthResponse> register(
            @RequestPart("data") RegisterRequest request,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture) {
        log.info("Registration attempt for username: {}", request.username());

        try {
            authService.registerUser(
                    request.username(),
                    request.email(),
                    request.password(),
                    request.encryptedPrivateKey(),
                    request.publicKey(),
                    request.salt(),
                    request.iv(),
                    profilePicture);

            AuthResponse authResponse = authService.authenticateUser(request.username(), request.password());

            log.info("Successfully registered user {}", request.username());
            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);

        } catch (IllegalArgumentException e) {
            log.warn("Registration failed for username {}: {}", request.username(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthResponse(false, e.getMessage(), null, null, null, null, null));
        }
    }
}