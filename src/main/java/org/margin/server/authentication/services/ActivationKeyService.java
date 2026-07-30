package org.margin.server.authentication.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.authentication.entities.ActivationKey;
import org.margin.server.authentication.repositories.ActivationKeyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class ActivationKeyService {

    public static final int WEEK_IN_SECONDS = 259200;
    private final ActivationKeyRepository activationKeyRepository;

    public ActivationKeyService(ActivationKeyRepository activationKeyRepository) {
        this.activationKeyRepository = activationKeyRepository;
    }

    @Transactional
    public ActivationKey generateActivationKey(Long userId) {
        ActivationKey activationKey = new ActivationKey();
        activationKey.setToken(UUID.randomUUID().toString());
        activationKey.setExpiresAt(Instant.now().plusSeconds(WEEK_IN_SECONDS));
        activationKey.setUserId(userId);
        return activationKeyRepository.save(activationKey);
    }

    @Transactional
    public void findAndConsumeActivationKey(String activationToken) {
        ActivationKey activationKey = activationKeyRepository.findActivationKeyByToken(activationToken).orElseThrow();

        if (activationKey.isExpired()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expired");
        }

        if (activationKey.isActivated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token already activated");
        }

        activationKey.setActivatedAt(Instant.now());
        activationKeyRepository.save(activationKey);

        log.info("Activation sucessfully consumed for user id {}", activationKey.getUserId());
    }

    public boolean isUserActivated(Long userId) {
        ActivationKey activationKey = activationKeyRepository.findActivationKeyByUserId(userId).orElseThrow();
        return activationKey.isActivated();
    }
}
