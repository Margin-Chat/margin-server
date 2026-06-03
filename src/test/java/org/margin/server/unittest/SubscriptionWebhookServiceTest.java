package org.margin.server.unittest;

import com.mollie.mollie.models.components.*;
import com.mollie.mollie.models.errors.APIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.email.EmailService;
import org.margin.server.notifications.NotificationType;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.social.margin.service.MarginService;
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
import org.margin.server.users.models.User;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.margin.server.unittest.utils.MarginTestUtils.createMargin;

@ExtendWith(MockitoExtension.class)
class SubscriptionWebhookServiceTest {

    @Mock
    private MollieClient mollieClient;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private EmailService emailService;
    @Mock
    private ConnectionManager connectionManager;
    @Mock
    private WebSocketMessageBuilder wsMessageBuilder;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MarginService marginService;

    private SubscriptionWebhookService service;

    private static final String CUSTOMER_ID = "cst_abc";
    private static final String PAYMENT_ID = "tr_xyz";
    private static final String SUBSCRIPTION_ID = "sub_def";

    @BeforeEach
    void setUp() {
        MollieProperties mollie = new MollieProperties("test_key", "https://api.mollie.com/v2",
                "https://example.com", "https://example.com", "true");
        SubscriptionPricingProperties.TierConfig smallConfig =
                new SubscriptionPricingProperties.TierConfig(new BigDecimal("9.99"), 25, 50, 10);
        SubscriptionPricingProperties pricing = new SubscriptionPricingProperties("EUR",
                Map.of(SubscriptionTier.SMALL, smallConfig));
        service = new SubscriptionWebhookService(mollieClient, subscriptionRepository,
                subscriptionService, mollie, pricing, emailService, connectionManager, wsMessageBuilder,
                notificationService, marginService, null);
        ReflectionTestUtils.setField(service, "self", service);
    }

    private Subscription localSubscription() {
        Margin margin = createMargin(1L, "Test Margin");

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
        Subscription subscription = localSubscriptionWithOwner();
        subscription.setPendingPaymentId(PAYMENT_ID);
        PaymentResponse payment = paidFirstPaymentWithAmount();
        SubscriptionResponse mollieSub = mockSubscriptionResponse(SUBSCRIPTION_ID, "2026-06-06");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(mollieClient.createSubscription(eq(CUSTOMER_ID), eq("9.99"), eq("EUR"), eq("1 month"),
                anyString(), anyString(), anyString())).thenReturn(mollieSub);

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
        PaymentResponse payment = mockPayment(PAYMENT_ID, "failed", CUSTOMER_ID, "first");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));

        service.handleWebhook(PAYMENT_ID);

        verify(subscriptionService, never()).applyTier(any(), any());
        verify(mollieClient, never()).createSubscription(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    void firstPaymentForUnknownCustomer_doesNothing() {
        PaymentResponse payment = mockPayment(PAYMENT_ID, "paid", "cst_unknown", "first");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId("cst_unknown")).thenReturn(Optional.empty());

        service.handleWebhook(PAYMENT_ID);

        verify(subscriptionService, never()).applyTier(any(), any());
    }

    @Test
    void firstPaymentReceivedTwice_secondCallSkippedIfAlreadyProcessed() {
        Subscription subscription = localSubscription();
        // pendingPaymentId is null → already processed
        PaymentResponse payment = paidFirstPayment();

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));

        service.handleWebhook(PAYMENT_ID);

        verify(subscriptionService, never()).applyTier(any(), any());
        verify(mollieClient, never()).createSubscription(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    void recurringPaymentPaid_extendsPeriod() {
        Subscription subscription = localSubscriptionWithOwner();
        subscription.setSubscriptionId(SUBSCRIPTION_ID);
        subscription.setTier(SubscriptionTier.SMALL);
        PaymentResponse payment = mockPayment(PAYMENT_ID, "paid", CUSTOMER_ID, "recurring");
        lenient().when(payment.amount()).thenReturn(Amount.builder().value("9.99").currency("EUR").build());
        SubscriptionResponse mollieSub = mockSubscriptionResponse(SUBSCRIPTION_ID, "2026-07-06");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(mollieClient.getSubscription(CUSTOMER_ID, SUBSCRIPTION_ID)).thenReturn(mollieSub);

        service.handleWebhook(PAYMENT_ID);

        verify(subscriptionRepository).save(subscription);
        assertThat(subscription.getCurrentPeriodEnd()).isNotNull();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(subscriptionService, never()).applyTier(any(), any());
    }

    @Test
    void firstPaymentMissingMetadata_doesNothing() {
        Subscription subscription = localSubscription();
        subscription.setPendingPaymentId(PAYMENT_ID);
        PaymentResponse payment = mockPayment(PAYMENT_ID, "paid", CUSTOMER_ID, "first");
        when(payment.metadata()).thenReturn(JsonNullable.undefined());

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));

        service.handleWebhook(PAYMENT_ID);

        verify(subscriptionService, never()).applyTier(any(), any());
    }

    @Test
    void failedPayment_clearsPendingAndSendsFailureNotification() {
        Subscription subscription = localSubscriptionWithOwner();
        subscription.setPendingPaymentId(PAYMENT_ID);
        PaymentResponse payment = mockPayment(PAYMENT_ID, "failed", CUSTOMER_ID, "first");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(wsMessageBuilder.buildMessage(any(), any(), any())).thenReturn("{}");

        service.handleWebhook(PAYMENT_ID);

        assertThat(subscription.getPendingPaymentId()).isNull();
        verify(subscriptionRepository).save(subscription);
        verify(notificationService).createForUsers(
                eq(List.of(ownerUserOf(subscription))),
                isNull(),
                eq(NotificationType.SUBSCRIPTION_PAYMENT_FAILED),
                isNull(),
                eq(subscription.getMargin().getId()));
        verify(connectionManager).sendToUser(eq(ownerUserOf(subscription).getId()), anyString());
    }

    @Test
    void firstPaymentPaid_sendsUpgradeNotificationAndWsPush() {
        Subscription subscription = localSubscriptionWithOwner();
        subscription.setPendingPaymentId(PAYMENT_ID);
        PaymentResponse payment = paidFirstPaymentWithAmount();
        SubscriptionResponse mollieSub = mockSubscriptionResponse(SUBSCRIPTION_ID, "2026-06-06");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(mollieClient.createSubscription(eq(CUSTOMER_ID), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(mollieSub);
        when(wsMessageBuilder.buildMessage(any(), any(), any())).thenReturn("{}");

        service.handleWebhook(PAYMENT_ID);

        verify(notificationService).createForUsers(
                eq(List.of(ownerUserOf(subscription))),
                isNull(),
                eq(NotificationType.SUBSCRIPTION_UPGRADED),
                isNull(),
                eq(subscription.getMargin().getId()));
        verify(connectionManager).sendToUser(eq(ownerUserOf(subscription).getId()), anyString());
    }

    @Test
    void firstPaymentPaid_sendsInvoiceEmail() throws Exception {
        Subscription subscription = localSubscriptionWithOwner();
        subscription.setPendingPaymentId(PAYMENT_ID);
        PaymentResponse payment = paidFirstPaymentWithAmount();
        SubscriptionResponse mollieSub = mockSubscriptionResponse(SUBSCRIPTION_ID, "2026-06-06");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(mollieClient.createSubscription(eq(CUSTOMER_ID), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(mollieSub);

        service.handleWebhook(PAYMENT_ID);

        verify(emailService).buildInvoiceMail(any(), any(), any(), any(), any(), any(), any());
        verify(emailService).sendEmail(eq(ownerUserOf(subscription).getEmail()), anyString(), any());
    }

    @Test
    void recurringPaymentPaid_sendsInvoiceEmail() throws Exception {
        Subscription subscription = localSubscriptionWithOwner();
        subscription.setSubscriptionId(SUBSCRIPTION_ID);
        subscription.setTier(SubscriptionTier.SMALL);
        subscription.setCurrentPeriodEnd(Instant.parse("2026-06-06T00:00:00Z"));
        PaymentResponse payment = mockPayment(PAYMENT_ID, "paid", CUSTOMER_ID, "recurring");
        when(payment.amount()).thenReturn(Amount.builder().value("9.99").currency("EUR").build());
        SubscriptionResponse mollieSub = mockSubscriptionResponse(SUBSCRIPTION_ID, "2026-07-06");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(mollieClient.getSubscription(CUSTOMER_ID, SUBSCRIPTION_ID)).thenReturn(mollieSub);

        service.handleWebhook(PAYMENT_ID);

        verify(emailService).buildInvoiceMail(any(), any(), any(), any(), any(), any(), any());
        verify(emailService).sendEmail(eq(ownerUserOf(subscription).getEmail()), anyString(), any());
    }

    @Test
    void firstPaymentPaid_cancelsExistingMollieSubscriptionBeforeCreatingNew() {
        Subscription subscription = localSubscriptionWithOwner();
        subscription.setPendingPaymentId(PAYMENT_ID);
        subscription.setSubscriptionId(SUBSCRIPTION_ID);
        PaymentResponse payment = paidFirstPaymentWithAmount();
        SubscriptionResponse mollieSub = mockSubscriptionResponse("sub_new", "2026-06-06");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(mollieClient.createSubscription(any(), any(), any(), any(), any(), any(), any())).thenReturn(mollieSub);

        service.handleWebhook(PAYMENT_ID);

        verify(mollieClient).cancelSubscription(CUSTOMER_ID, SUBSCRIPTION_ID);
        assertThat(subscription.getSubscriptionId()).isEqualTo("sub_new");
    }

    @Test
    void reconcileStalePendingPayments_callsHandleWebhookForEachStaleSub() {
        Subscription subscription = localSubscription();
        subscription.setPendingPaymentId(PAYMENT_ID);
        PaymentResponse payment = mockPayment(PAYMENT_ID, "open", CUSTOMER_ID, "first");

        when(subscriptionRepository.findStalePendingPayments(any())).thenReturn(List.of(subscription));
        when(subscriptionRepository.findByMargin(subscription.getMargin())).thenReturn(Optional.of(subscription));
        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));

        service.reconcileStalePendingPayments();

        verify(mollieClient).getPayment(PAYMENT_ID);
    }

    @Test
    void reconcileStalePendingPayments_skipsWhenNoPendingPaymentsAreStale() {
        when(subscriptionRepository.findStalePendingPayments(any())).thenReturn(List.of());

        service.reconcileStalePendingPayments();

        verify(mollieClient, never()).getPayment(anyString());
    }

    @Test
    void reconcileStalePendingPayments_continuesAfterExceptionOnOneSub() {
        Subscription sub1 = localSubscription();
        sub1.getMargin().setId(1L);
        sub1.setPendingPaymentId(PAYMENT_ID);

        Subscription sub2 = localSubscription();
        sub2.getMargin().setId(2L);
        sub2.setMollieCustomerId("cst_second");
        sub2.setPendingPaymentId("tr_second");

        PaymentResponse payment2 = mockPayment("tr_second", "open", "cst_second", "first");

        when(subscriptionRepository.findStalePendingPayments(any())).thenReturn(List.of(sub1, sub2));
        when(subscriptionRepository.findByMargin(sub1.getMargin()))
                .thenThrow(new RuntimeException("db error"));
        when(subscriptionRepository.findByMargin(sub2.getMargin())).thenReturn(Optional.of(sub2));
        when(mollieClient.getPayment("tr_second")).thenReturn(payment2);
        when(subscriptionRepository.findByMollieCustomerId("cst_second")).thenReturn(Optional.of(sub2));

        service.reconcileStalePendingPayments();

        verify(mollieClient, never()).getPayment(PAYMENT_ID);
        verify(mollieClient).getPayment("tr_second");
    }

    @Test
    void cancelPendingPayment_clearsIdWhenWebhookNotYetProcessed() {
        Subscription subscription = localSubscription();
        Margin margin = subscription.getMargin();
        subscription.setPendingPaymentId(PAYMENT_ID);
        PaymentResponse payment = mockPayment(PAYMENT_ID, "open", CUSTOMER_ID, "first");

        when(subscriptionRepository.findByMargin(margin)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);

        service.cancelPendingPayment(margin);

        assertThat(subscription.getPendingPaymentId()).isNull();
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void reconcilePendingPayment_delegatesToHandleWebhook() {
        Subscription subscription = localSubscription();
        Margin margin = subscription.getMargin();
        subscription.setPendingPaymentId(PAYMENT_ID);
        PaymentResponse payment = mockPayment(PAYMENT_ID, "open", CUSTOMER_ID, "first");

        when(subscriptionRepository.findByMargin(margin)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);

        service.reconcilePendingPayment(margin);

        verify(mollieClient).getPayment(PAYMENT_ID);
    }

    private Subscription localSubscriptionWithOwner() {
        User owner = createUser(99L, "Owner", "owner@test.com");

        MarginMember ownerMember = new MarginMember();
        ownerMember.setUser(owner);
        ownerMember.setRole(MarginRole.OWNER);

        Subscription subscription = localSubscription();
        subscription.getMargin().getMembers().add(ownerMember);
        lenient().when(marginService.getOwner(subscription.getMargin().getId())).thenReturn(ownerMember);
        return subscription;
    }

    private static User ownerUserOf(Subscription subscription) {
        return subscription.getMargin().getMembers().stream()
                .filter(m -> m.getRole() == MarginRole.OWNER)
                .findFirst().orElseThrow().getUser();
    }

    private static PaymentResponse mockPayment(String id, String status, String customerId, String sequenceType) {
        PaymentResponse payment = mock(PaymentResponse.class);
        lenient().when(payment.id()).thenReturn(id);
        lenient().when(payment.status()).thenReturn(PaymentResponseStatus.of(status));
        lenient().when(payment.customerId()).thenReturn(Optional.of(customerId));
        lenient().when(payment.sequenceType()).thenReturn(SequenceTypeResponse.of(sequenceType));
        return payment;
    }

    private static PaymentResponse paidFirstPayment() {
        PaymentResponse payment = mockPayment(PAYMENT_ID, "paid", CUSTOMER_ID, "first");
        lenient().when(payment.metadata()).thenReturn(JsonNullable.of(Metadata.of("SMALL")));
        return payment;
    }

    private static PaymentResponse paidFirstPaymentWithAmount() {
        PaymentResponse payment = paidFirstPayment();
        lenient().when(payment.amount()).thenReturn(Amount.builder().value("9.99").currency("EUR").build());
        return payment;
    }

    @Test
    void firstPaymentPaid_whenNextPaymentDateUndefined_usesStartDateAsFallback() {
        Subscription subscription = localSubscriptionWithOwner();
        subscription.setPendingPaymentId(PAYMENT_ID);
        PaymentResponse payment = paidFirstPaymentWithAmount();
        SubscriptionResponse mollieSub = mock(SubscriptionResponse.class);
        lenient().when(mollieSub.id()).thenReturn(SUBSCRIPTION_ID);
        when(mollieSub.nextPaymentDate()).thenReturn(JsonNullable.undefined());
        when(mollieSub.startDate()).thenReturn("2026-06-22");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(mollieClient.createSubscription(any(), any(), any(), any(), any(), any(), any())).thenReturn(mollieSub);

        service.handleWebhook(PAYMENT_ID);

        assertThat(subscription.getCurrentPeriodEnd()).isNotNull();
        verify(subscriptionService).applyTier(any(), eq(SubscriptionTier.SMALL));
    }

    @Test
    void firstPaymentPaid_whenMollieSubscriptionAlreadyExists_recoversAndCompletes() {
        Subscription subscription = localSubscriptionWithOwner();
        subscription.setPendingPaymentId(PAYMENT_ID);
        PaymentResponse payment = paidFirstPaymentWithAmount();

        APIException alreadyExists = mock(APIException.class);
        when(alreadyExists.code()).thenReturn(422);
        when(alreadyExists.bodyAsString()).thenReturn(Optional.of(
                "{\"detail\":\"A subscription with the same description already exists for this customer\"}"));

        ListSubscriptionResponse existing = mock(ListSubscriptionResponse.class);
        when(existing.id()).thenReturn(SUBSCRIPTION_ID);
        when(existing.description()).thenReturn("margin Test Margin — SMALL plan");

        SubscriptionResponse recovered = mockSubscriptionResponse(SUBSCRIPTION_ID, "2026-06-22");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(subscriptionRepository.findByMollieCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(subscription));
        when(mollieClient.createSubscription(any(), any(), any(), any(), any(), any(), any())).thenThrow(alreadyExists);
        when(mollieClient.listSubscriptions(CUSTOMER_ID)).thenReturn(List.of(existing));
        when(mollieClient.getSubscription(CUSTOMER_ID, SUBSCRIPTION_ID)).thenReturn(recovered);

        service.handleWebhook(PAYMENT_ID);

        assertThat(subscription.getSubscriptionId()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(subscription.getPendingPaymentId()).isNull();
        verify(subscriptionService).applyTier(any(), eq(SubscriptionTier.SMALL));
    }

    private static SubscriptionResponse mockSubscriptionResponse(String id, String nextPaymentDate) {
        SubscriptionResponse sub = mock(SubscriptionResponse.class);
        lenient().when(sub.id()).thenReturn(id);
        lenient().when(sub.nextPaymentDate()).thenReturn(JsonNullable.of(nextPaymentDate));
        return sub;
    }
}