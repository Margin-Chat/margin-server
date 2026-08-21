package org.margin.server.users.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.shared.membership.MarginMembershipLookup;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Reaps guest accounts whose expiry has passed.
 *
 * TODO: needs a distributed lock before horizontal scaling — this job deletes rows, so a
 * double run across instances would matter.
 */
@Slf4j
@Service
public class GuestCleanupService {

    private static final int BATCH_SIZE = 500;

    private final UserRepository userRepository;
    private final UserCacheService userCacheService;
    private final MarginMembershipLookup marginMembershipLookup;

    @Value("${margin.guests.cleanup-enabled:true}")
    private boolean enabled;

    public GuestCleanupService(UserRepository userRepository,
                               UserCacheService userCacheService,
                               MarginMembershipLookup marginMembershipLookup) {
        this.userRepository = userRepository;
        this.userCacheService = userCacheService;
        this.marginMembershipLookup = marginMembershipLookup;
    }

    @Scheduled(cron = "0 15 * * * *")
    public void scheduledCleanup() {
        if (!enabled) {
            return;
        }
        int removed = removeExpiredGuests(Instant.now());
        if (removed > 0) {
            log.info("Removed {} expired guest account(s)", removed);
        }
    }

    /**
     * Deletes one batch of expired guests. Each row is deleted independently so one failure
     * cannot abort the rest.
     */
    @Transactional
    public int removeExpiredGuests(Instant now) {
        List<User> expired = userRepository.findExpiredGuests(now, PageRequest.of(0, BATCH_SIZE));
        int removed = 0;

        for (User guest : expired) {
            // Should always be false: guests are barred from every membership write path.
            // If it ever trips, containment has broken somewhere and this is the tripwire.
            if (marginMembershipLookup.hasAnyMembership(guest.getId())) {
                log.warn("Guest {} holds margin membership and was not removed — guest containment "
                        + "has been breached somewhere", guest.getId());
                continue;
            }

            try {
                userRepository.delete(guest);
                userCacheService.evictUserCache(guest.getId());
                removed++;
            } catch (Exception e) {
                log.warn("Could not remove guest {}: {}", guest.getId(), e.getMessage());
            }
        }

        return removed;
    }
}
