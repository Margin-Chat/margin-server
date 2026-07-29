package org.margin.server.subscriptions.services;

import com.mollie.mollie.models.components.Amount;
import com.mollie.mollie.models.components.ListSubscriptionResponse;
import com.mollie.mollie.models.components.Metadata;
import com.mollie.mollie.models.components.PaymentResponse;
import com.mollie.mollie.models.components.SubscriptionResponse;
import com.mollie.mollie.models.errors.APIException;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.email.EmailService;
import org.margin.server.shared.notifications.NotificationType;
import org.margin.server.subscriptions.events.SubscriptionStatusChangedEvent;
import org.margin.server.social.api.MarginLookup;
import org.margin.server.users.api.UserLookup;

import org.margin.server.subscriptions.config.MollieProperties;
import org.margin.server.subscriptions.config.SubscriptionPricingProperties;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.events.SubscriptionUpdatedEvent;
import org.margin.server.subscriptions.models.SubscriptionStatus;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.models.dtos.SubscriptionDTO;
import org.margin.server.subscriptions.repositories.SubscriptionRepository;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SubscriptionWebhookService {
    private static final Duration STALE_PENDING_THRESHOLD = Duration.ofMinutes(10);
    private final MollieClient mollieClient;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final MollieProperties mollieProperties;
    private final SubscriptionPricingProperties pricingProperties;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final MarginLookup marginLookup;
    private final UserLookup userLookup;
    private final SubscriptionWebhookService self;

    public SubscriptionWebhookService(MollieClient mollieClient,
                                      SubscriptionRepository subscriptionRepository,
                                      SubscriptionService subscriptionService,
                                      MollieProperties mollieProperties,
                                      SubscriptionPricingProperties pricingProperties,
                                      EmailService emailService,
                                      ApplicationEventPublisher eventPublisher,
                                      MarginLookup marginLookup,
                                      UserLookup userLookup,
                                      @Lazy SubscriptionWebhookService self) {
        this.mollieClient = mollieClient;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.mollieProperties = mollieProperties;
        this.pricingProperties = pricingProperties;
        this.emailService = emailService;
        this.eventPublisher = eventPublisher;
        this.marginLookup = marginLookup;
        this.userLookup = userLookup;
        this.self = self;
    }

    @Transactional
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void reconcileStalePendingPayments() {
        Instant cutoff = Instant.now().minus(STALE_PENDING_THRESHOLD);
        List<Subscription> stale = subscriptionRepository.findStalePendingPayments(cutoff);
        if (stale.isEmpty()) return;

        log.info("Reconciling {} stale pending payments", stale.size());

        for (Subscription subscription : stale) {
            try {
                self.reconcilePendingPayment(subscription.getMarginId());
            } catch (Exception e) {
                log.warn("Failed to reconcile stale pending payment for subscription {}",
                        subscription.getId(), e);
            }
        }
    }

    @Transactional
    public SubscriptionDTO reconcilePendingPayment(Long marginId) {
        Subscription subscription = subscriptionRepository.findByMarginId(marginId).orElseThrow();
        if (subscription.getPendingPaymentId() != null) {
            try {
                self.handleWebhook(subscription.getPendingPaymentId());
            } catch (Exception e) {
                log.warn("Failed to reconcile pending payment {} for margin {}",
                        subscription.getPendingPaymentId(), marginId, e);
            }
        }
        return subscriptionService.getSubscriptionDtoForMargin(marginId);
    }

    @Transactional
    public SubscriptionDTO cancelPendingPayment(Long marginId) {
        self.reconcilePendingPayment(marginId);

        Subscription subscription = subscriptionRepository.findByMarginId(marginId).orElseThrow();
        if (subscription.getPendingPaymentId() != null) {
            log.info("Cancelling pending payment {} for margin {}",
                    subscription.getPendingPaymentId(), marginId);
            subscription.setPendingPaymentId(null);
            subscription.setPendingTier(null);
            subscriptionRepository.save(subscription);
        }
        return subscriptionService.getSubscriptionDtoForMargin(marginId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleWebhook(String paymentId) {
        PaymentResponse payment = mollieClient.getPayment(paymentId);
        String status = payment.status().value();
        Optional<String> customerId = payment.customerId();
        String sequenceType = payment.sequenceType().value();

        if (customerId.isEmpty()) {
            log.warn("Webhook payment {} has no customerId, ignoring", paymentId);
            return;
        }

        Subscription subscription = subscriptionRepository.findByMollieCustomerId(customerId.orElse(null)).orElse(null);
        if (subscription == null) {
            log.warn("Webhook for unknown Mollie customer {}", customerId);
            return;
        }

        if ("failed".equals(status) || "expired".equals(status) || "canceled".equals(status)) {
            subscription.setPendingPaymentId(null);
            subscription.setPendingTier(null);
            if ("recurring".equals(sequenceType) && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                // Renewal failed: keep paid limits until period end, then the
                // expiry scheduler reverts to FREE. A later successful recurring
                // payment sets the subscription back to ACTIVE.
                subscription.setStatus(SubscriptionStatus.PAST_DUE);
                log.info("Recurring payment {} failed, subscription {} marked PAST_DUE",
                        paymentId, subscription.getId());
            }
            subscriptionRepository.save(subscription);
            notifyOwner(subscription, NotificationType.SUBSCRIPTION_PAYMENT_FAILED);
            pushSubscriptionUpdate(subscription);

            log.info("Payment failed for subscription {}", subscription.getId());
        }
        if (!"paid".equals(status)) {
            return;
        }

        if ("first".equals(sequenceType)) {
            handleFirstPaymentPaid(subscription, payment);
        } else if ("recurring".equals(sequenceType)) {
            handleRecurringPaymentPaid(subscription, payment);
        } else {
            log.info("Payment {} has sequenceType={}, ignoring", paymentId, sequenceType);
        }
    }

    private void handleFirstPaymentPaid(Subscription subscription, PaymentResponse payment) {
        if (subscription.getPendingPaymentId() == null) {
            log.info("Payment {} already processed for subscription {}, skipping", payment.id(), subscription.getId());
            return;
        }

        if (subscription.getSubscriptionId() != null) {
            try {
                mollieClient.cancelSubscription(subscription.getMollieCustomerId(), subscription.getSubscriptionId());
                log.info("Cancelled old Mollie subscription {} for upgrade on subscription {}",
                        subscription.getSubscriptionId(), subscription.getId());
            } catch (Exception e) {
                log.warn("Failed to cancel old Mollie subscription {} during upgrade", subscription.getSubscriptionId(), e);
            }
            subscription.setSubscriptionId(null);
        }

        JsonNullable<Metadata> metadata = payment.metadata();
        if (!metadata.isPresent() || metadata.get().value() == null) {
            log.warn("First payment {} missing targetTier metadata", payment.id());
            return;
        }

        SubscriptionTier targetTier = SubscriptionTier.valueOf((String) metadata.get().value());
        BigDecimal price = pricingProperties.tiers().get(targetTier).price();
        if (price == null) {
            log.warn("No price configured for tier {}", targetTier);
            return;
        }

        String description = "margin %s — %s plan".formatted(marginLookup.summaryOf(subscription.getMarginId()).name(), targetTier);
        SubscriptionResponse mollieSubscription;
        try {
            mollieSubscription = mollieClient.createSubscription(
                    subscription.getMollieCustomerId(),
                    price.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    pricingProperties.currency(),
                    "1 month",
                    description,
                    "%s/api/subscriptions/mollie/webhook".formatted(mollieProperties.webhookBaseUrl()),
                    LocalDate.now(ZoneOffset.UTC).plusMonths(1).toString()
            );
        } catch (APIException e) {
            if (e.code() == 422 && e.bodyAsString().map(b -> b.contains("already exists")).orElse(false)) {
                log.warn("Mollie subscription already exists for customer {}, recovering", subscription.getMollieCustomerId());
                mollieSubscription = mollieClient.listSubscriptions(subscription.getMollieCustomerId())
                        .stream()
                        .filter(s -> description.equals(s.description()))
                        .findFirst()
                        .map(s -> mollieClient.getSubscription(subscription.getMollieCustomerId(), s.id()))
                        .orElseThrow(() -> e);
            } else {
                throw e;
            }
        }

        String nextDate = mollieSubscription.nextPaymentDate().isPresent()
                ? mollieSubscription.nextPaymentDate().get()
                : mollieSubscription.startDate();
        subscription.setSubscriptionId(mollieSubscription.id());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(Instant.now());
        subscription.setCurrentPeriodEnd(parseMollieDate(nextDate));
        subscription.setPendingPaymentId(null);
        subscription.setPendingTier(null);

        subscriptionService.applyTier(subscription, targetTier);

        log.info("First payment processed: margin subscription {} -> tier {} (Mollie sub {})",
                subscription.getId(), targetTier, subscription.getSubscriptionId());

        sendPaymentConfirmation(subscription, payment, targetTier);
        notifyOwner(subscription, NotificationType.SUBSCRIPTION_UPGRADED);
        pushSubscriptionUpdate(subscription);
    }

    private void handleRecurringPaymentPaid(Subscription subscription, PaymentResponse payment) {
        if (subscription.getSubscriptionId() == null) {
            log.warn("Recurring payment but no Mollie subscription on local subscription {}", subscription.getId());
            return;
        }
        SubscriptionResponse mollieSubscription = mollieClient.getSubscription(
                subscription.getMollieCustomerId(), subscription.getSubscriptionId());
        String nextDateStr = mollieSubscription.nextPaymentDate().isPresent()
                ? mollieSubscription.nextPaymentDate().get()
                : null;
        Instant nextPaymentDate = parseMollieDate(nextDateStr);
        subscription.setCurrentPeriodStart(Instant.now());
        subscription.setCurrentPeriodEnd(nextPaymentDate);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);
        log.info("Recurring payment renewed subscription {}, next at {}", subscription.getId(), nextPaymentDate);

        sendPaymentConfirmation(subscription, payment, subscription.getTier());
        pushSubscriptionUpdate(subscription);
    }

    private void notifyOwner(Subscription subscription, NotificationType type) {
        try {
            Long ownerUserId = marginLookup.ownerUserIdOf(subscription.getMarginId());
            eventPublisher.publishEvent(new SubscriptionStatusChangedEvent(
                    ownerUserId, type, subscription.getMarginId()));
        } catch (Exception e) {
            log.warn("Failed to send {} notification for subscription {}", type, subscription.getId(), e);
        }
    }

    private void pushSubscriptionUpdate(Subscription subscription) {
        try {
            Long ownerUserId = marginLookup.ownerUserIdOf(subscription.getMarginId());
            SubscriptionDTO dto = subscriptionService.getSubscriptionDtoForMargin(subscription.getMarginId());
            eventPublisher.publishEvent(new SubscriptionUpdatedEvent(ownerUserId, dto));
        } catch (Exception e) {
            log.warn("Failed to push subscription update for subscription {}", subscription.getId(), e);
        }
    }

    private void sendPaymentConfirmation(Subscription subscription, PaymentResponse payment, SubscriptionTier tier) {
        try {
            UserLookup.UserContact owner = userLookup.contactOf(marginLookup.ownerUserIdOf(subscription.getMarginId()));
            Amount amount = payment.amount();
            String html = emailService.buildInvoiceMail(
                    owner.displayName(),
                    marginLookup.summaryOf(subscription.getMarginId()).name(),
                    tier.name(),
                    amount.value(),
                    amount.currency(),
                    payment.id(),
                    subscription.getCurrentPeriodEnd()
            );
            emailService.sendEmail(
                    owner.email(),
                    "Payment receipt — " + marginLookup.summaryOf(subscription.getMarginId()).name(),
                    html
            );
        } catch (Exception e) {
            log.warn("Failed to send invoice email for subscription {}", subscription.getId(), e);
            // TODO: Handle failed email
        }
    }

    private Instant parseMollieDate(String dateOrNull) {
        if (dateOrNull == null) return null;
        return LocalDate.parse(dateOrNull).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}