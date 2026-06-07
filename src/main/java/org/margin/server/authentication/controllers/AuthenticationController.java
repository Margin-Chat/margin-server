package org.margin.server.authentication.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.models.*;
import org.margin.server.authentication.services.ActivationKeyService;
import org.margin.server.authentication.services.AuthenticationService;
import org.margin.server.authentication.services.PasswordResetService;
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
    private final PasswordResetService passwordResetService;

    public AuthenticationController(AuthenticationService authenticationService,
                                    RateLimitService rateLimitService,
                                    ActivationKeyService activationKeyService,
                                    PasswordResetService passwordResetService) {
        this.authenticationService = authenticationService;
        this.rateLimitService = rateLimitService;
        this.activationKeyService = activationKeyService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authenticationService.authenticateUser(
                request.email(),
                request.password());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal User user) {
        authenticationService.logoutUser(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthResponse> register(
            HttpServletRequest httpServletRequest,
            @RequestPart("data") RegisterRequest request,
            @RequestPart(value = "profilePicture", required = false) MultipartFile profilePicture) {
        rateLimitRegistration(httpServletRequest);

        try {
            authenticationService.registerUser(
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
            log.warn("Registration failed for email {}: {}", request.email(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthResponse(false, e.getMessage(), null, null, null, null, null));
        } catch (Exception e) {
            log.error("Unexpected error during registration for email {}: {}", request.email(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuthResponse(false, "Registration failed", null, null, null, null, null));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(HttpServletRequest httpServletRequest,
                                               @RequestBody ForgotPasswordRequest request) {
        rateLimitForgotPassword(httpServletRequest);
        passwordResetService.requestPasswordReset(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(HttpServletRequest httpServletRequest,
                                              @RequestBody ResetPasswordRequest request) {
        rateLimitResetPassword(httpServletRequest);
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/activate/{token}")
    public ResponseEntity<Void> activate(@PathVariable String token) {
        activationKeyService.findAndConsumeActivationKey(token);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/encryption-keys")
    public ResponseEntity<Void> updateEncryptionKeys(@RequestBody EncryptionKeysRequest request,
                                                     @AuthenticationPrincipal User user) {
        authenticationService.updateEncryptionKeys(user, request.publicKey(), request.encryptedPrivateKey(), request.salt(), request.iv());
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

    private void rateLimitForgotPassword(HttpServletRequest httpServletRequest) {
        String ip = httpServletRequest.getHeader("X-Forwarded-For");
        if (ip == null) ip = httpServletRequest.getRemoteAddr();

        String key = "forgot-password:" + ip;
        if (!rateLimitService.tryConsume(key, RateLimitConfig.forgotPassword())) {
            throw new TooManyRequestsException("Too many password reset requests. Try again later.");
        }
    }

    private void rateLimitResetPassword(HttpServletRequest httpServletRequest) {
        String ip = httpServletRequest.getHeader("X-Forwarded-For");
        if (ip == null) ip = httpServletRequest.getRemoteAddr();

        String key = "reset-password:" + ip;
        if (!rateLimitService.tryConsume(key, RateLimitConfig.resetPassword())) {
            throw new TooManyRequestsException("Too many password reset attempts. Try again later.");
        }
    }
}