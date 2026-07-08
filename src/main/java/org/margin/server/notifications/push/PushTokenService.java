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

    /**
     * Upserts by token: a device's token is stable across logins, so when a different
     * account signs in on the same device the token must move to that account — otherwise
     * the previous user would keep receiving the new user's notifications.
     */
    @Transactional
    public void register(User user, PushPlatform platform, String token) {
        PushToken entity = pushTokenRepository.findByToken(token).orElseGet(PushToken::new);
        if (entity.getId() == null) {
            entity.setCreatedAt(Instant.now());
        }
        entity.setUser(user);
        entity.setPlatform(platform);
        entity.setToken(token);
        entity.setUpdatedAt(Instant.now());
        pushTokenRepository.save(entity);
    }

    /** Only removes the token if it belongs to the caller — logout must not be able to
     * silence another user's device. */
    @Transactional
    public void unregister(User user, String token) {
        pushTokenRepository.findByToken(token)
                .filter(t -> t.getUser().getId().equals(user.getId()))
                .ifPresent(pushTokenRepository::delete);
    }
}
