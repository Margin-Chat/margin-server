package org.margin.server.subscriptions.services;

import org.margin.server.social.margin.entities.Margin;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.entities.SubscriptionLimits;
import org.margin.server.subscriptions.factories.SubscriptionLimitsFactory;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.repositories.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public void createSubscriptionForMargin(Margin margin, SubscriptionTier tier) {
        Subscription subscription = new Subscription();
        subscription.setMargin(margin);
        subscription.setTier(tier);

        SubscriptionLimits limits = SubscriptionLimitsFactory.forTier(SubscriptionTier.FREE);
        limits.setSubscription(subscription);
        subscription.setLimits(limits);

        subscriptionRepository.save(subscription);

        log.info("Created subscription for margin {}", subscription);
    }
}
