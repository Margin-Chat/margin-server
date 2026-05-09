package org.margin.server.subscriptions.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.subscriptions.config.MollieProperties;
import org.margin.server.subscriptions.config.SubscriptionPricingProperties;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.models.SubscriptionStatus;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.repositories.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

@Slf4j
@Service
public class SubscriptionWebhookService {

    private final MollieClient mollieClient;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final MollieProperties mollieProperties;
    private final SubscriptionPricingProperties pricingProperties;

    public SubscriptionWebhookService(MollieClient mollieClient,
                                      SubscriptionRepository subscriptionRepository,
                                      SubscriptionService subscriptionService,
                                      MollieProperties mollieProperties,
                                      SubscriptionPricingProperties pricingProperties) {
        this.mollieClient = mollieClient;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.mollieProperties = mollieProperties;
        this.pricingProperties = pricingProperties;
    }

    @Transactional
    public void handleWebhook(String paymentId) {
        Map<String, Object> payment = mollieClient.getPayment(paymentId);
        String status = (String) payment.get("status");
        String customerId = (String) payment.get("customerId");
        String sequenceType = (String) payment.get("sequenceType");

        if (customerId == null) {
            log.warn("Webhook payment {} has no customerId, ignoring", paymentId);
            return;
        }

        Subscription subscription = subscriptionRepository.findByMollieCustomerId(customerId).orElse(null);
        if (subscription == null) {
            log.warn("Webhook for unknown Mollie customer {}", customerId);
            return;
        }

        if (!"paid".equals(status)) {
            log.info("Payment {} status={}, no action", paymentId, status);
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

    private void handleFirstPaymentPaid(Subscription subscription, Map<String, Object> payment) {
        if (subscription.getSubscriptionId() != null) {
            log.info("First payment for subscription {} already processed, skipping", subscription.getId());
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> metadata = (Map<String, String>) payment.get("metadata");
        if (metadata == null || metadata.get("targetTier") == null) {
            log.warn("First payment {} missing targetTier metadata", payment.get("id"));
            return;
        }

        SubscriptionTier targetTier = SubscriptionTier.valueOf(metadata.get("targetTier"));
        BigDecimal price = pricingProperties.prices().get(targetTier);
        if (price == null) {
            log.warn("No price configured for tier {}", targetTier);
            return;
        }

        Map<String, Object> mollieSubscription = mollieClient.createSubscription(
                subscription.getMollieCustomerId(),
                price.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                pricingProperties.currency(),
                "1 month",
                "margin %s — %s plan".formatted(subscription.getMargin().getName(), targetTier),
                "%s/api/subscriptions/mollie/webhook".formatted(mollieProperties.webhookBaseUrl())
        );

        subscription.setSubscriptionId((String) mollieSubscription.get("id"));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(Instant.now());
        subscription.setCurrentPeriodEnd(parseMollieDate((String) mollieSubscription.get("nextPaymentDate")));

        subscriptionService.applyTier(subscription, targetTier);

        log.info("First payment processed: margin subscription {} -> tier {} (Mollie sub {})",
                subscription.getId(), targetTier, subscription.getSubscriptionId());
    }

    private void handleRecurringPaymentPaid(Subscription subscription, Map<String, Object> payment) {
        if (subscription.getSubscriptionId() == null) {
            log.warn("Recurring payment but no Mollie subscription on local subscription {}", subscription.getId());
            return;
        }
        Map<String, Object> mollieSubscription = mollieClient.getSubscription(
                subscription.getMollieCustomerId(), subscription.getSubscriptionId());
        Instant nextPaymentDate = parseMollieDate((String) mollieSubscription.get("nextPaymentDate"));
        subscription.setCurrentPeriodStart(Instant.now());
        subscription.setCurrentPeriodEnd(nextPaymentDate);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);
        log.info("Recurring payment renewed subscription {}, next at {}", subscription.getId(), nextPaymentDate);
    }

    private Instant parseMollieDate(String dateOrNull) {
        if (dateOrNull == null) return null;
        return LocalDate.parse(dateOrNull).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}