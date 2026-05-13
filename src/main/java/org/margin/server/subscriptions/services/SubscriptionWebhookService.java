package org.margin.server.subscriptions.services;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.email.EmailService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.subscriptions.config.MollieProperties;
import org.margin.server.subscriptions.config.SubscriptionPricingProperties;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.models.SubscriptionStatus;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.models.dtos.SubscriptionDTO;
import org.margin.server.subscriptions.repositories.SubscriptionRepository;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
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
    private final EmailService emailService;
    private final ConnectionManager connectionManager;
    private final WebSocketMessageBuilder wsMessageBuilder;

    public SubscriptionWebhookService(MollieClient mollieClient,
                                      SubscriptionRepository subscriptionRepository,
                                      SubscriptionService subscriptionService,
                                      MollieProperties mollieProperties,
                                      SubscriptionPricingProperties pricingProperties,
                                      EmailService emailService,
                                      ConnectionManager connectionManager,
                                      WebSocketMessageBuilder wsMessageBuilder) {
        this.mollieClient = mollieClient;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
        this.mollieProperties = mollieProperties;
        this.pricingProperties = pricingProperties;
        this.emailService = emailService;
        this.connectionManager = connectionManager;
        this.wsMessageBuilder = wsMessageBuilder;
    }

    @Transactional
    public SubscriptionDTO reconcilePendingPayment(Margin margin) {
        Subscription subscription = subscriptionRepository.findByMargin(margin).orElseThrow();
        if (subscription.getPendingPaymentId() != null) {
            try {
                handleWebhook(subscription.getPendingPaymentId());
            } catch (Exception e) {
                log.warn("Failed to reconcile pending payment {} for margin {}",
                        subscription.getPendingPaymentId(), margin.getId(), e);
            }
        }
        return subscriptionService.getSubscriptionDtoForMargin(margin);
    }

    @Transactional
    public SubscriptionDTO cancelPendingPayment(Margin margin) {
        // Reconcile first so we don't clear a payment that actually went through
        reconcilePendingPayment(margin);

        Subscription subscription = subscriptionRepository.findByMargin(margin).orElseThrow();
        if (subscription.getPendingPaymentId() != null) {
            log.info("Cancelling pending payment {} for margin {}",
                    subscription.getPendingPaymentId(), margin.getId());
            subscription.setPendingPaymentId(null);
            subscription.setPendingTier(null);
            subscriptionRepository.save(subscription);
        }
        return subscriptionService.getSubscriptionDtoForMargin(margin);
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
            if ("failed".equals(status) || "expired".equals(status) || "canceled".equals(status)) {
                subscription.setPendingPaymentId(null);
                subscription.setPendingTier(null);
                subscriptionRepository.save(subscription);
            }
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
        if (subscription.getPendingPaymentId() == null) {
            log.info("Payment {} already processed for subscription {}, skipping", payment.get("id"), subscription.getId());
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
                "%s/api/subscriptions/mollie/webhook".formatted(mollieProperties.webhookBaseUrl()),
                null
        );

        subscription.setSubscriptionId((String) mollieSubscription.get("id"));
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(Instant.now());
        subscription.setCurrentPeriodEnd(parseMollieDate((String) mollieSubscription.get("nextPaymentDate")));
        subscription.setPendingPaymentId(null);
        subscription.setPendingTier(null);

        subscriptionService.applyTier(subscription, targetTier);

        log.info("First payment processed: margin subscription {} -> tier {} (Mollie sub {})",
                subscription.getId(), targetTier, subscription.getSubscriptionId());

        sendInvoiceEmail(subscription, payment, targetTier);
        pushSubscriptionUpdate(subscription);
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

        sendInvoiceEmail(subscription, payment, subscription.getTier());
        pushSubscriptionUpdate(subscription);
    }

    private void pushSubscriptionUpdate(Subscription subscription) {
        subscription.getMargin().getMembers().stream()
                .filter(m -> m.getRole() == MarginRole.OWNER)
                .findFirst()
                .ifPresent(owner -> {
                    try {
                        SubscriptionDTO dto = subscriptionService.getSubscriptionDtoForMargin(subscription.getMargin());
                        String json = wsMessageBuilder.buildMessage(
                                WebSocketMessageType.SUBSCRIPTION_UPDATED,
                                owner.getUser().getId(),
                                dto
                        );
                        connectionManager.sendToUser(owner.getUser().getId(), json);
                    } catch (Exception e) {
                        log.warn("Failed to push subscription update for subscription {}", subscription.getId(), e);
                    }
                });
    }

    private void sendInvoiceEmail(Subscription subscription, Map<String, Object> payment, SubscriptionTier tier) {
        subscription.getMargin().getMembers().stream()
                .filter(m -> m.getRole() == MarginRole.OWNER)
                .findFirst()
                .ifPresent(owner -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> amountMap = (Map<String, Object>) payment.get("amount");
                        String html = emailService.buildInvoiceMail(
                                owner.getUser().getDisplayName(),
                                subscription.getMargin().getName(),
                                tier.name(),
                                (String) amountMap.get("value"),
                                (String) amountMap.get("currency"),
                                (String) payment.get("id"),
                                subscription.getCurrentPeriodEnd()
                        );
                        emailService.sendEmail(
                                owner.getUser().getEmail(),
                                "Payment receipt — " + subscription.getMargin().getName(),
                                html
                        );
                    } catch (MessagingException e) {
                        log.warn("Failed to send invoice email for subscription {}", subscription.getId(), e);
                    }
                });
    }

    private Instant parseMollieDate(String dateOrNull) {
        if (dateOrNull == null) return null;
        return LocalDate.parse(dateOrNull).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}