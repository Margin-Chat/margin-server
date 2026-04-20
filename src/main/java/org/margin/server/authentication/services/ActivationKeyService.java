package org.margin.server.authentication.services;

import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.authentication.repositories.ActivationKeyRepository;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ActivationKeyService {

    public static final int WEEK_IN_SECONDS = 259200;
    private final ActivationKeyRepository activationKeyRepository;

    public ActivationKeyService(ActivationKeyRepository activationKeyRepository) {
        this.activationKeyRepository = activationKeyRepository;
    }

    @Transactional
    public ActivationKey generateActivationKey() {
        ActivationKey activationKey = new ActivationKey();
        activationKey.setToken("123");
        activationKey.setExpiresAt(Instant.now().plusSeconds(WEEK_IN_SECONDS));
        return activationKeyRepository.save(activationKey);
    }

//    @Transactional
//    public ActivationKey findActivationKeyByToken(String token) {
//
//    }

    @Transactional
    public void consumeActivationKey(ActivationKey activationKey, User user) {
        activationKey.setActivatedAt(Instant.now());
        activationKey.setUser(user);
        activationKeyRepository.save(activationKey);
    }
}
