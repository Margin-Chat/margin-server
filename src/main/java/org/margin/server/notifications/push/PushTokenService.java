package org.margin.server.notifications.push;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;

    public PushTokenService(PushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = pushTokenRepository;
    }

    @Transactional
    public void register(Long userId, PushPlatform platform, String token) {
        PushToken entity = pushTokenRepository.findByToken(token).orElseGet(PushToken::new);
        if (entity.getId() == null) {
            entity.setCreatedAt(Instant.now());
        }
        entity.setUserId(userId);
        entity.setPlatform(platform);
        entity.setToken(token);
        entity.setUpdatedAt(Instant.now());
        pushTokenRepository.save(entity);
    }

    @Transactional
    public void unregister(Long userId, String token) {
        pushTokenRepository.findByToken(token)
                .filter(t -> t.getUserId().equals(userId))
                .ifPresent(pushTokenRepository::delete);
    }
}
