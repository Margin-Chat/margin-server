package org.margin.server.integrationtest.utils;

import org.margin.server.authentication.controllers.AuthenticationController;
import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.authentication.repositories.ActivationKeyRepository;
import org.margin.server.authentication.services.ActivationKeyService;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ActivationTestUtils {

    private static AuthenticationController authenticationController;
    private static ActivationKeyService activationKeyService;
    private static ActivationKeyRepository activationKeyRepository;

    @Autowired
    public ActivationTestUtils(AuthenticationController authenticationController,
                               ActivationKeyService activationKeyService,
                               ActivationKeyRepository activationKeyRepository) {
        ActivationTestUtils.authenticationController = authenticationController;
        ActivationTestUtils.activationKeyService = activationKeyService;
        ActivationTestUtils.activationKeyRepository = activationKeyRepository;
    }

    public static ActivationKey generateActivationKey(User user) {
        return activationKeyService.generateActivationKey(user);
    }

    public static ActivationKey generateExpiredActivationKey(User user) {
        ActivationKey key = new ActivationKey();
        key.setUser(user);
        key.setToken(UUID.randomUUID().toString());
        key.setExpiresAt(Instant.now().minusSeconds(60));
        return activationKeyRepository.save(key);
    }

    public static ResponseEntity<Void> activate(String token) {
        return authenticationController.activate(token);
    }

    public static ActivationKey findByToken(String token) {
        return activationKeyRepository.findActivationKeyByToken(token).orElseThrow();
    }

    public static boolean isUserActivated(User user) {
        return activationKeyService.isUserActivated(user);
    }
}
