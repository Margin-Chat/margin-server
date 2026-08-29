package org.margin.server.integrationtest.utils;

import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.authentication.entities.BetaKey;
import org.margin.server.authentication.entities.UserSecurity;
import org.margin.server.authentication.models.AuthResponse;
import org.margin.server.authentication.repositories.ActivationKeyRepository;
import org.margin.server.authentication.repositories.BetaKeyRepository;
import org.margin.server.authentication.services.ActivationKeyService;
import org.margin.server.authentication.services.UserSecurityService;
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
    private static ActivationKeyService activationKeyService;
    private static UserSecurityService userSecurityService;
    private static ActivationKeyRepository activationKeyRepository;

    @Autowired
    public AuthTestUtils(AuthenticationService authenticationService,
                         BetaKeyRepository betaKeyRepository,
                         UserRepository userRepository,
                         ActivationKeyService activationKeyService,
                         UserSecurityService userSecurityService,
                         ActivationKeyRepository activationKeyRepository) {
        AuthTestUtils.activationKeyRepository = activationKeyRepository;
        AuthTestUtils.authenticationService = authenticationService;
        AuthTestUtils.betaKeyRepository = betaKeyRepository;
        AuthTestUtils.userRepository = userRepository;
        AuthTestUtils.activationKeyService = activationKeyService;
        AuthTestUtils.userSecurityService = userSecurityService;
    }

    public static BetaKey createBetaKey() {
        BetaKey key = new BetaKey();
        key.setKey(UUID.randomUUID().toString());
        key.setCreatedAt(Instant.now());
        return betaKeyRepository.save(key);
    }

    public static ActivationKey register(String displayName, String email, String password, String betaKey) {
        return authenticationService.registerUser(
                displayName, email, password,
                "enc-private-key", "public-key", "salt", "iv", null);
    }

    public static void registerAndActivate(String displayName, String email, String password, String betaKey) {
        ActivationKey activationKey = register(displayName, email, password, betaKey);
        activationKeyService.findAndConsumeActivationKey(activationKey.getToken());
    }

    public static void activate(Long userId) {
        ActivationKey key = activationKeyRepository.findActivationKeyByUserId(userId).orElseThrow();
        activationKeyService.findAndConsumeActivationKey(key.getToken());
    }

    public static AuthResponse login(String email, String password) {
        return authenticationService.authenticateUser(email, password);
    }

    public static User findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase()).orElseThrow();
    }

    public static UserSecurity securityOf(User user) {
        return userSecurityService.get(user.getId());
    }
}
