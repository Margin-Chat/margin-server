package org.margin.server.integrationtest.utils;

import org.margin.server.authentication.entities.BetaKey;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.authentication.repositories.BetaKeyRepository;
import org.margin.server.authentication.services.AuthenticationService;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AuthTestUtils {

    private static AuthenticationService authenticationService;
    private static BetaKeyRepository betaKeyRepository;
    private static UserRepository userRepository;

    @Autowired
    public AuthTestUtils(AuthenticationService authenticationService,
                         BetaKeyRepository betaKeyRepository,
                         UserRepository userRepository) {
        AuthTestUtils.authenticationService = authenticationService;
        AuthTestUtils.betaKeyRepository = betaKeyRepository;
        AuthTestUtils.userRepository = userRepository;
    }

    public static BetaKey createBetaKey() {
        BetaKey key = new BetaKey();
        key.setKey(UUID.randomUUID().toString());
        key.setCreatedAt(Instant.now());
        return betaKeyRepository.save(key);
    }

    public static AuthResponse register(String handle, String email, String password, String betaKey) {
        authenticationService.registerUser(
                handle, handle, email, password,
                "enc-private-key", "public-key", "salt", "iv",
                betaKey, null);
        return authenticationService.authenticateUser(email, password);
    }

    public static AuthResponse login(String email, String password) {
        return authenticationService.authenticateUser(email, password);
    }

    public static User findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase()).orElseThrow();
    }
}
