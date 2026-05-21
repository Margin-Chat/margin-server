package org.margin.server.subscriptions.services;

import com.mollie.mollie.models.components.CustomerResponse;
import com.mollie.mollie.models.components.PaymentResponse;
import com.mollie.mollie.models.components.SubscriptionResponse;
import com.mollie.mollie.models.components.Url;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.subscriptions.config.MollieProperties;
import org.margin.server.subscriptions.config.SubscriptionPricingProperties;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.entities.SubscriptionLimits;
import org.margin.server.subscriptions.exceptions.SubscriptionNotFoundException;
import org.margin.server.subscriptions.factories.SubscriptionLimitsFactory;
import org.margin.server.subscriptions.models.SubscriptionStatus;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.models.dtos.SubscriptionDTO;
import org.margin.server.subscriptions.models.dtos.SubscriptionLimitsDTO;
import org.margin.server.subscriptions.repositories.SubscriptionRepository;
import org.margin.server.subscriptions.utils.SubscriptionUtils;
import org.margin.server.users.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPricingProperties subscriptionPricingProperties;
    private final MollieClient mollieClient;
    private final MollieProperties mollieProperties;
    private final SubscriptionLimitsFactory subscriptionLimitsFactory;
    private final SubscriptionService self;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               SubscriptionPricingProperties subscriptionPricingProperties,
                               MollieClient mollieClient,
                               MollieProperties mollieProperties,
                               SubscriptionLimitsFactory subscriptionLimitsFactory,
                               @Lazy SubscriptionService self) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionPricingProperties = subscriptionPricingProperties;
        this.mollieClient = mollieClient;
        this.mollieProperties = mollieProperties;
        this.subscriptionLimitsFactory = subscriptionLimitsFactory;
        this.self = self;
    }

    public Subscription getByMargin(Margin margin) {
        return subscriptionRepository.findByMargin(margin)
                .orElseThrow(() -> new SubscriptionNotFoundException(margin.getId()));
    }

    @Transactional
    public void createSubscriptionForMargin(Margin margin, SubscriptionTier tier) {
        Subscription subscription = new Subscription();
        subscription.setMargin(margin);
        subscription.setTier(tier);

        SubscriptionLimits limits = subscriptionLimitsFactory.forTier(SubscriptionTier.FREE);
        limits.setSubscription(subscription);
        subscription.setLimits(limits);

        subscriptionRepository.save(subscription);

        log.info("Created subscription for margin {}", subscription.getSubscriptionId());
    }

    @Transactional(readOnly = true)
    public SubscriptionDTO getSubscriptionDtoForMargin(Margin margin) {
        Subscription subscription = getByMargin(margin);
        SubscriptionLimits limits = subscription.getLimits();
        return new SubscriptionDTO(
                subscription.getTier(),
                subscription.getStatus(),
                new SubscriptionLimitsDTO(
                        limits.getMaxMembers(),
                        limits.getMaxStorageGb(),
                        limits.getMaxCallParticipants()
                ),
                subscription.getMargin().getMembers().size(),
                subscription.getTrialEndsAt(),
                subscription.getCurrentPeriodEnd(),
                subscription.getPendingPaymentId() != null,
                subscription.getPendingTier()
        );
    }

    @Transactional
    public SubscriptionDTO downgrade(Margin margin, SubscriptionTier newTier) {
        Subscription subscription = getByMargin(margin);

        if (!newTier.isLowerTier(subscription.getTier())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Margin cannot be upgrade without a checkout");
        }

        if (subscription.getSubscriptionId() != null) {
            try {
                mollieClient.cancelSubscription(subscription.getMollieCustomerId(), subscription.getSubscriptionId());
            } catch (Exception e) {
                log.warn("Failed to cancel Mollie subscription {} during tier change", subscription.getSubscriptionId(), e);
            }
        }

        BigDecimal price = subscriptionPricingProperties.tiers().get(newTier).price();
        String startDate = subscription.getCurrentPeriodEnd() != null
                ? SubscriptionUtils.getNextSubscriptionDate(subscription).toString()
                : null;

        SubscriptionResponse mollieSubscription = mollieClient.createSubscription(
                subscription.getMollieCustomerId(),
                price.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                subscriptionPricingProperties.currency(),
                "1 month",
                "margin %s — %s plan".formatted(margin.getName(), newTier),
                "%s/api/subscriptions/mollie/webhook".formatted(mollieProperties.webhookBaseUrl()),
                startDate
        );

        subscription.setSubscriptionId(mollieSubscription.id());
        self.applyTier(subscription, newTier);

        return self.getSubscriptionDtoForMargin(margin);
    }

    @Transactional
    public void cancelSubscription(Margin margin) {
        Subscription subscription = getByMargin(margin);
        if (subscription.getSubscriptionId() != null) {
            mollieClient.cancelSubscription(subscription.getMollieCustomerId(), subscription.getSubscriptionId());
        }
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(subscription);
        log.info("Cancelled subscription {} for margin {}", subscription.getId(), margin.getId());
    }

    @Transactional
    public void applyTier(Subscription subscription, SubscriptionTier tier) {
        SubscriptionLimits desired = subscriptionLimitsFactory.forTier(tier);
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
        BigDecimal price = subscriptionPricingProperties.tiers().get(targetTier).price();

        Subscription subscription = getByMargin(margin);

        String customerId = subscription.getMollieCustomerId();
        if (customerId == null) {
            CustomerResponse customer = mollieClient.createCustomer(margin.getName(), user.getEmail());
            customerId = customer.id();
            subscription.setMollieCustomerId(customerId);
            subscriptionRepository.save(subscription);
            log.info("Created Mollie customer {} for margin {}", customerId, margin.getId());
        }

        if (subscription.getPendingPaymentId() != null) {
            throw new IllegalStateException(
                    "Subscription %d already has a pending payment %s — reconcile or cancel it first"
                            .formatted(subscription.getId(), subscription.getPendingPaymentId()));
        }

        PaymentResponse payment = mollieClient.createFirstPayment(
                customerId,
                price.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                subscriptionPricingProperties.currency(),
                "margin %s — %s plan".formatted(margin.getName(), targetTier),
                "%s/payment-return?marginId=%d".formatted(mollieProperties.redirectBaseUrl(), margin.getId()),
                "%s/api/subscriptions/mollie/webhook".formatted(mollieProperties.webhookBaseUrl()),
                targetTier.name()
        );

        log.info("Created payment for subscription {} for margin {}", subscription.getId(), margin.getName());

        subscription.setPendingPaymentId(payment.id());
        subscription.setPendingTier(targetTier);
        subscriptionRepository.save(subscription);

        Url checkout = payment.links().checkout().orElseThrow();
        return checkout.href();
    }

    @Transactional
    @Scheduled(fixedDelayString = "${scheduling.subscription-cleanup-delay-ms:3600000}")
    public void updateExpiredSubscriptions() {
        List<Subscription> expiredSubscriptions = subscriptionRepository.findExpiredSubscriptions(Instant.now());
        if (expiredSubscriptions.isEmpty()) return;

        log.info("Reverting {} expired cancelled subscriptions to FREE", expiredSubscriptions.size());
        for (Subscription subscription : expiredSubscriptions) {
            self.applyTier(subscription, SubscriptionTier.FREE);
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCurrentPeriodStart(null);
            subscription.setCurrentPeriodEnd(null);
            log.info("Reverting {} expired subscription to free", subscription.getId());
        }
    }
}