package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.subscriptions.config.MollieProperties;
import org.margin.server.subscriptions.config.SubscriptionPricingProperties;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.entities.SubscriptionLimits;
import org.margin.server.subscriptions.models.SubscriptionStatus;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.repositories.SubscriptionRepository;
import org.margin.server.subscriptions.services.MollieClient;
import org.margin.server.subscriptions.services.SubscriptionService;
import org.margin.server.subscriptions.services.SubscriptionWebhookService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionWebhookServiceTest {

    @Mock private MollieClient mollieClient;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionService subscriptionService;

    private SubscriptionWebhookService service;

    private static final String CUSTOMER_ID = "cst_abc";
    private static final String PAYMENT_ID = "tr_xyz";
    private static final String SUBSCRIPTION_ID = "sub_def";

    @BeforeEach
    void setUp() {
        MollieProperties mollie = new MollieProperties("test_key", "https://api.mollie.com/v2",
                "https://example.com", "https://example.com");
        SubscriptionPricingProperties pricing = new SubscriptionPricingProperties("EUR",
                Map.of(SubscriptionTier.SMALL, new BigDecimal("9.99")));
        service = new SubscriptionWebhookService(mollieClient, subscriptionRepository,
                subscriptionService, mollie, pricing);
    }

    private Subscription localSubscription() {
        Margin margin = new Margin();
        margin.setId(1L);
        margin.setName("Test Margin");

        SubscriptionLimits limits = new SubscriptionLimits();
        limits.setMaxMembers(25);
        limits.setMaxStorageGb(5);
        limits.setMaxCallParticipants(10);

        Subscription sub = new Subscription();
        sub.setId(42L);
        sub.setMargin(margin);
        sub.setMollieCustomerId(CUSTOMER_ID);
        sub.setTier(SubscriptionTier.FREE);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setLimits(limits);
        return sub;
    }

    @Test
    void firstPaymentPaid_createsMollieSubscriptionAndAppliesTier() {
        Subscription subscription = localSubscription();
        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(Map.of(
                "id", PAYMENT_ID,
                "status", "paid",
                "customerId", CUSTOMER_ID,
                "sequenceType", "first",
                "metadata", Map.of("targetTier", "SMALL", "marginId", "1")
        ));
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(mollieClient.createSubscription(eq(CUSTOMER_ID), eq("9.99"), eq("EUR"), eq("1 month"),
                anyString(), anyString())).thenReturn(Map.of(
                "id", SUBSCRIPTION_ID,
                "nextPaymentDate", "2026-06-06"
        ));

        service.handleWebhook(PAYMENT_ID);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionService).applyTier(captor.capture(), eq(SubscriptionTier.SMALL));
        assertThat(captor.getValue().getSubscriptionId()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(captor.getValue().getCurrentPeriodEnd()).isNotNull();
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void firstPaymentNotPaid_doesNothing() {
        Subscription subscription = localSubscription();
        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(Map.of(
                "id", PAYMENT_ID,
                "status", "failed",
                "customerId", CUSTOMER_ID,
                "sequenceType", "first"
        ));
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));

        service.handleWebhook(PAYMENT_ID);

        verify(subscriptionService, never()).applyTier(any(), any());
        verify(mollieClient, never()).createSubscription(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void firstPaymentForUnknownCustomer_doesNothing() {
        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(Map.of(
                "id", PAYMENT_ID,
                "status", "paid",
                "customerId", "cst_unknown",
                "sequenceType", "first"
        ));
        when(subscriptionRepository.findByMollieCustomerId("cst_unknown")).thenReturn(Optional.empty());

        service.handleWebhook(PAYMENT_ID);

        verify(subscriptionService, never()).applyTier(any(), any());
    }

    @Test
    void firstPaymentReceivedTwice_secondCallSkippedIfAlreadyProcessed() {
        Subscription subscription = localSubscription();
        subscription.setSubscriptionId(SUBSCRIPTION_ID);
        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(Map.of(
                "id", PAYMENT_ID,
                "status", "paid",
                "customerId", CUSTOMER_ID,
                "sequenceType", "first",
                "metadata", Map.of("targetTier", "SMALL", "marginId", "1")
        ));
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));

        service.handleWebhook(PAYMENT_ID);

        verify(subscriptionService, never()).applyTier(any(), any());
        verify(mollieClient, never()).createSubscription(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void recurringPaymentPaid_extendsPeriod() {
        Subscription subscription = localSubscription();
        subscription.setSubscriptionId(SUBSCRIPTION_ID);
        subscription.setTier(SubscriptionTier.SMALL);
        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(Map.of(
                "id", PAYMENT_ID,
                "status", "paid",
                "customerId", CUSTOMER_ID,
                "sequenceType", "recurring"
        ));
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(mollieClient.getSubscription(CUSTOMER_ID, SUBSCRIPTION_ID)).thenReturn(Map.of(
                "id", SUBSCRIPTION_ID,
                "nextPaymentDate", "2026-07-06"
        ));

        service.handleWebhook(PAYMENT_ID);

        verify(subscriptionRepository).save(subscription);
        assertThat(subscription.getCurrentPeriodEnd()).isNotNull();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(subscriptionService, never()).applyTier(any(), any());
    }

    @Test
    void firstPaymentMissingMetadata_doesNothing() {
        Subscription subscription = localSubscription();
        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(Map.of(
                "id", PAYMENT_ID,
                "status", "paid",
                "customerId", CUSTOMER_ID,
                "sequenceType", "first"
        ));
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));

        service.handleWebhook(PAYMENT_ID);

        verify(subscriptionService, never()).applyTier(any(), any());
    }
}