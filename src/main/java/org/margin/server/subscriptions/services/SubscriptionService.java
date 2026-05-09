package org.margin.server.subscriptions.services;

import org.margin.server.social.margin.entities.Margin;
import org.margin.server.subscriptions.config.MollieProperties;
import org.margin.server.subscriptions.config.SubscriptionPricingProperties;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.entities.SubscriptionLimits;
import org.margin.server.subscriptions.factories.SubscriptionLimitsFactory;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.models.dtos.SubscriptionDTO;
import org.margin.server.subscriptions.models.dtos.SubscriptionLimitsDTO;
import org.margin.server.subscriptions.repositories.SubscriptionRepository;
import org.margin.server.users.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPricingProperties subscriptionPricingProperties;
    private final MollieClient mollieClient;
    private final MollieProperties mollieProperties;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, SubscriptionPricingProperties subscriptionPricingProperties, MollieClient mollieClient, MollieProperties mollieProperties) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionPricingProperties = subscriptionPricingProperties;
        this.mollieClient = mollieClient;
        this.mollieProperties = mollieProperties;
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

    @Transactional(readOnly = true)
    public SubscriptionDTO getSubscriptionDtoForMargin(Margin margin) {
        Subscription subscription = subscriptionRepository.findByMargin(margin).orElseThrow();
        SubscriptionLimits limits = subscription.getLimits();
        return new SubscriptionDTO(
                subscription.getTier(),
                subscription.getStatus(),
                new SubscriptionLimitsDTO(
                        limits.getMaxMembers(),
                        limits.getMaxStorageGb(),
                        limits.getMaxCallParticipants()
                ),
                margin.getMembers().size(),
                subscription.getTrialEndsAt(),
                subscription.getCurrentPeriodEnd()
        );
    }

    @Transactional
    public void applyTier(Subscription subscription, SubscriptionTier tier) {
        SubscriptionLimits desired = SubscriptionLimitsFactory.forTier(tier);
        SubscriptionLimits current = subscription.getLimits();
        current.setMaxMembers(desired.getMaxMembers());
        current.setMaxStorageGb(desired.getMaxStorageGb());
        current.setMaxCallParticipants(desired.getMaxCallParticipants());
        subscription.setTier(tier);
        log.info("Applied tier {} to subscription {}", tier, subscription.getId());
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public String createCheckout(Margin margin, SubscriptionTier targetTier, User user) {
        BigDecimal price = subscriptionPricingProperties.prices().get(targetTier);

        Subscription subscription = subscriptionRepository.findByMargin(margin).orElseThrow();

        String customerId = subscription.getMollieCustomerId();
        if (customerId == null) {
            Map<String, Object> customer = mollieClient.createCustomer(margin.getName(), user.getEmail());
            customerId = (String) customer.get("id");
            subscription.setMollieCustomerId(customerId);
            subscriptionRepository.save(subscription);
            log.info("Created Mollie customer {} for margin {}", customerId, margin.getId());
        }

        Map<String, Object> payment = mollieClient.createFirstPayment(
                customerId,
                price.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                subscriptionPricingProperties.currency(),
                "margin %s — %s plan".formatted(margin.getName(), targetTier),
                "%s/payment-return?marginId=%d".formatted(mollieProperties.redirectBaseUrl(), margin.getId()),
                "%s/api/subscriptions/mollie/webhook".formatted(mollieProperties.webhookBaseUrl()),
                Map.of(
                        "marginId", String.valueOf(margin.getId()),
                        "targetTier", targetTier.name()
                )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> links = (Map<String, Object>) payment.get("_links");
        @SuppressWarnings("unchecked")
        Map<String, Object> checkout = (Map<String, Object>) links.get("checkout");
        return (String) checkout.get("href");
    }
}
