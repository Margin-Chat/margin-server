package org.margin.server.notifications.push;

import org.margin.server.users.models.User;
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
    public void register(User user, PushPlatform platform, String token) {
        PushToken entity = pushTokenRepository.findByToken(token).orElseGet(PushToken::new);
        if (entity.getId() == null) {
            entity.setCreatedAt(Instant.now());
        }
        entity.setUserId(user.getId());
        entity.setPlatform(platform);
        entity.setToken(token);
        entity.setUpdatedAt(Instant.now());
        pushTokenRepository.save(entity);
    }

    @Transactional
    public void unregister(User user, String token) {
        pushTokenRepository.findByToken(token)
                .filter(t -> t.getUserId().equals(user.getId()))
                .ifPresent(pushTokenRepository::delete);
    }
}
