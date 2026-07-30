package org.margin.server.authentication.controllers;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.authentication.repositories.ActivationKeyRepository;
import org.margin.server.authentication.services.AuthenticationService;
import org.margin.server.users.models.User;
import org.margin.server.users.api.UserLookup;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@Profile("dev")
@Slf4j
class TestSupportController {

    record ActivateTestRequest(String email, String secret) {
    }

    record CreateTestUserRequest(
            String email,
            String password,
            String displayName,
            String publicKey,
            String encryptedPrivateKey,
            String salt,
            String iv,
            String secret
    ) {
    }

    private final UserLookup userLookup;
    private final ActivationKeyRepository activationKeyRepository;
    private final AuthenticationService authenticationService;
    private final Environment env;

    TestSupportController(UserLookup userLookup,
                          ActivationKeyRepository activationKeyRepository,
                          AuthenticationService authenticationService,
                          Environment env) {
        this.userLookup = userLookup;
        this.activationKeyRepository = activationKeyRepository;
        this.authenticationService = authenticationService;
        this.env = env;
    }

    @PostMapping("/activate-test")
    ResponseEntity<Void> activateForTest(@RequestBody ActivateTestRequest request) {
        if (!validSecret(request.secret())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        User user = userLookup.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No user with email: " + request.email()));

        ActivationKey key = activationKeyRepository.findActivationKeyByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No activation key for: " + request.email()));

        if (!key.isActivated()) {
            key.setActivatedAt(Instant.now());
            activationKeyRepository.save(key);
            log.info("Test-activated account: {}", request.email());
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/register-and-activate-test")
    ResponseEntity<Void> registerAndActivateForTest(@RequestBody CreateTestUserRequest request) {
        if (!validSecret(request.secret())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        try {
            ActivationKey key = authenticationService.registerUser(
                    request.displayName(),
                    request.email(),
                    request.password(),
                    request.encryptedPrivateKey(),
                    request.publicKey(),
                    request.salt(),
                    request.iv(),
                    null);

            key.setActivatedAt(Instant.now());
            activationKeyRepository.save(key);
            log.info("Test-created and activated user: {}", request.email());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        return ResponseEntity.ok().build();
    }

    private boolean validSecret(String secret) {
        return env.getProperty("TEST_ACTIVATION_SECRET", "dev-test-secret").equals(secret);
    }
}
