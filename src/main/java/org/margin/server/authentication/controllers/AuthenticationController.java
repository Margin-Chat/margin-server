package org.margin.server.authentication.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.authentication.models.LoginRequest;
import org.margin.server.authentication.models.RegisterRequest;
import org.margin.server.authentication.services.ActivationKeyService;
import org.margin.server.authentication.services.AuthenticationService;
import org.margin.server.config.ratelimit.RateLimitConfig;
import org.margin.server.config.ratelimit.RateLimitService;
import org.margin.server.exceptions.TooManyRequestsException;
import org.margin.server.users.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthenticationController {
    private final AuthenticationService authenticationService;
    private final RateLimitService rateLimitService;
    private final ActivationKeyService activationKeyService;

    public AuthenticationController(AuthenticationService authenticationService,
                                    RateLimitService rateLimitService,
                                    ActivationKeyService activationKeyService) {
        this.authenticationService = authenticationService;
        this.rateLimitService = rateLimitService;
        this.activationKeyService = activationKeyService;
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
            HttpServletRequest httpServletRequest,
            @RequestPart("data") RegisterRequest request,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture) {
        rateLimitRegistration(httpServletRequest);

        log.info("Registration attempt for handle: {}", request.handle());

        try {
            authenticationService.registerUser(
                    request.handle(),
                    request.displayName(),
                    request.email(),
                    request.password(),
                    request.encryptedPrivateKey(),
                    request.publicKey(),
                    request.salt(),
                    request.iv(),
                    profilePicture);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Registration failed for handle {}: {}", request.handle(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthResponse(false, e.getMessage(), null, null, null, null, null));
        } catch (Exception e) {
            log.error("Unexpected error during registration for handle {}: {}", request.handle(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuthResponse(false, "Registration failed", null, null, null, null, null));
        }
    }

    @PostMapping(value = "/activate/{token}")
    public ResponseEntity<Void> activate(@PathVariable String token) {
        log.info("Activate attempt for token: {}", token);
        activationKeyService.findAndConsumeActivationKey(token);
        return ResponseEntity.ok().build();
    }

    private void rateLimitRegistration(HttpServletRequest httpServletRequest) {
        String ip = httpServletRequest.getHeader("X-Forwarded-For");
        if (ip == null) ip = httpServletRequest.getRemoteAddr();

        String key = "register:" + ip;
        if (!rateLimitService.tryConsume(key, RateLimitConfig.register())) {
            throw new TooManyRequestsException("Too many registration attempts. Try again later.");
        }
    }
}