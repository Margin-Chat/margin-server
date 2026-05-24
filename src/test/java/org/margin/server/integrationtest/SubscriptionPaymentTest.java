package org.margin.server.integrationtest;

import com.mollie.mollie.models.components.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.NotificationTestUtils;
import org.margin.server.integrationtest.utils.SubscriptionTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.notifications.NotificationType;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.subscriptions.SubscriptionController;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.models.SubscriptionStatus;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.models.dtos.CheckoutRequest;
import org.margin.server.subscriptions.services.SubscriptionWebhookService;
import org.margin.server.users.models.User;
import org.mockito.Mockito;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionPaymentTest extends MarginTestRunner {

    private static final String CUSTOMER_ID = "cst_inttest";
    private static final String PAYMENT_ID = "tr_inttest";
    private static final String SUBSCRIPTION_ID = "sub_inttest";

    @Autowired
    private SubscriptionWebhookService subscriptionWebhookService;

    @Autowired
    private SubscriptionController subscriptionController;

    private User owner;
    private Margin margin;

    @BeforeEach
    void setUp() {
        owner = UserTestUtils.createUser("payowner", "payowner@margin.chat");
        margin = MarginTestUtils.createMargin("PayMargin", owner);
        SubscriptionTestUtils.setMollieCustomerId(margin, CUSTOMER_ID);
    }

    @Test
    void duplicateCheckout_throwsWhenPaymentAlreadyPending() {
        SubscriptionTestUtils.setPendingPayment(margin, PAYMENT_ID, SubscriptionTier.SMALL);

        assertThrows(IllegalStateException.class, () ->
                subscriptionController.startCheckout(
                        new CheckoutRequest(margin.getId(), SubscriptionTier.SMALL), owner));
    }

    @Test
    void webhookFirstPaymentPaid_activatesSubscriptionAndClearsPending() {
        SubscriptionTestUtils.setPendingPayment(margin, PAYMENT_ID, SubscriptionTier.SMALL);
        PaymentResponse payment = paidFirstPaymentWithAmount();
        SubscriptionResponse mollieSub = mockSubscriptionResponse(SUBSCRIPTION_ID, "2026-06-13");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(mollieClient.createSubscription(eq(CUSTOMER_ID), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(mollieSub);

        subscriptionWebhookService.handleWebhook(PAYMENT_ID);

        Subscription sub = SubscriptionTestUtils.getForMargin(margin);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getTier()).isEqualTo(SubscriptionTier.SMALL);
        assertThat(sub.getPendingPaymentId()).isNull();
        assertThat(sub.getSubscriptionId()).isEqualTo(SUBSCRIPTION_ID);
    }

    @Test
    void webhookFirstPaymentPaid_sendsUpgradeNotification() {
        SubscriptionTestUtils.setPendingPayment(margin, PAYMENT_ID, SubscriptionTier.SMALL);
        PaymentResponse payment = paidFirstPaymentWithAmount();
        SubscriptionResponse mollieSub = mockSubscriptionResponse(SUBSCRIPTION_ID, "2026-06-13");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(mollieClient.createSubscription(eq(CUSTOMER_ID), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(mollieSub);

        subscriptionWebhookService.handleWebhook(PAYMENT_ID);

        assertThat(NotificationTestUtils.hasNotification(owner, NotificationType.SUBSCRIPTION_UPGRADED)).isTrue();
    }

    @Test
    void webhookFailedPayment_clearsPendingAndSendsNotification() {
        SubscriptionTestUtils.setPendingPayment(margin, PAYMENT_ID, SubscriptionTier.SMALL);
        PaymentResponse payment = mockPayment(PAYMENT_ID, "failed", CUSTOMER_ID, "first");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);

        subscriptionWebhookService.handleWebhook(PAYMENT_ID);

        Subscription sub = SubscriptionTestUtils.getForMargin(margin);
        assertThat(sub.getPendingPaymentId()).isNull();
        assertThat(sub.getPendingTier()).isNull();
        assertThat(NotificationTestUtils.hasNotification(owner, NotificationType.SUBSCRIPTION_PAYMENT_FAILED)).isTrue();
    }

    @Test
    void cancelPending_clearsPaymentIdWhenNotYetProcessed() {
        SubscriptionTestUtils.setPendingPayment(margin, PAYMENT_ID, SubscriptionTier.SMALL);
        PaymentResponse payment = mockPayment(PAYMENT_ID, "open", CUSTOMER_ID, "first");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);

        subscriptionWebhookService.cancelPendingPayment(margin);

        Subscription sub = SubscriptionTestUtils.getForMargin(margin);
        assertThat(sub.getPendingPaymentId()).isNull();
        assertThat(sub.getPendingTier()).isNull();
    }

    @Test
    void memberLimitWarning_notifiesOwnerAtNinetyPercent() {
        // maxMembers=2, threshold=ceil(2*0.9)=2 — warning fires when 2nd member is added
        SubscriptionTestUtils.overrideLimits(margin, 2, 5, 10);
        User extra = UserTestUtils.createUser("payextra", "payextra@margin.chat");

        MarginTestUtils.addUserToMargin(margin.getId(), owner, extra);

        assertThat(NotificationTestUtils.hasNotification(owner, NotificationType.SUBSCRIPTION_LIMIT_WARNING)).isTrue();
    }

    @Test
    void changeTier_forbiddenForNonOwner() {
        User nonOwner = UserTestUtils.createUser("notowner", "notowner@margin.chat");
        MarginTestUtils.addUserToMargin(margin.getId(), owner, nonOwner);

        assertThrows(ResponseStatusException.class, () ->
                subscriptionController.changeTier(margin.getId(), Map.of("tier", "SMALL"), nonOwner));
    }

    @Test
    void cancelSubscription_setsStatusToCancelled() {
        SubscriptionTestUtils.setActiveSubscription(margin, CUSTOMER_ID, "sub_old", SubscriptionTier.SMALL);

        subscriptionController.cancelSubscription(margin.getId(), owner);

        Subscription sub = SubscriptionTestUtils.getForMargin(margin);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void cancelSubscription_doesNotRevertTierOrLimits() {
        SubscriptionTestUtils.setActiveSubscription(margin, CUSTOMER_ID, "sub_old", SubscriptionTier.SMALL);

        subscriptionController.cancelSubscription(margin.getId(), owner);

        Subscription sub = SubscriptionTestUtils.getForMargin(margin);
        assertThat(sub.getTier()).isEqualTo(SubscriptionTier.SMALL);
        assertThat(sub.getLimits().getMaxMembers()).isEqualTo(25);
    }

    @Test
    void cancelSubscription_callsMollieCancelSubscription() {
        SubscriptionTestUtils.setActiveSubscription(margin, CUSTOMER_ID, "sub_old", SubscriptionTier.SMALL);

        subscriptionController.cancelSubscription(margin.getId(), owner);

        verify(mollieClient).cancelSubscription(CUSTOMER_ID, "sub_old");
    }

    @Test
    void cancelSubscription_forbiddenForNonOwner() {
        User nonOwner = UserTestUtils.createUser("notowner2", "notowner2@margin.chat");
        MarginTestUtils.addUserToMargin(margin.getId(), owner, nonOwner);

        assertThrows(ResponseStatusException.class, () ->
                subscriptionController.cancelSubscription(margin.getId(), nonOwner));
    }

    @Test
    void webhookFirstPaymentPaid_whenNextPaymentDateUndefined_usesStartDateAndActivates() {
        SubscriptionTestUtils.setPendingPayment(margin, PAYMENT_ID, SubscriptionTier.SMALL);
        PaymentResponse payment = paidFirstPaymentWithAmount();
        SubscriptionResponse mollieSub = SubscriptionTestUtils.mockSubscriptionResponseWithStartDate(SUBSCRIPTION_ID, "2026-06-22");

        when(mollieClient.getPayment(PAYMENT_ID)).thenReturn(payment);
        when(mollieClient.createSubscription(eq(CUSTOMER_ID), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(mollieSub);

        subscriptionWebhookService.handleWebhook(PAYMENT_ID);

        Subscription sub = SubscriptionTestUtils.getForMargin(margin);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.getTier()).isEqualTo(SubscriptionTier.SMALL);
        assertThat(sub.getPendingPaymentId()).isNull();
        assertThat(sub.getCurrentPeriodEnd()).isNotNull();
    }

    private static PaymentResponse mockPayment(String id, String status, String customerId, String sequenceType) {
        PaymentResponse payment = Mockito.mock(PaymentResponse.class);
        Mockito.lenient().when(payment.id()).thenReturn(id);
        Mockito.lenient().when(payment.status()).thenReturn(PaymentResponseStatus.of(status));
        Mockito.lenient().when(payment.customerId()).thenReturn(Optional.of(customerId));
        Mockito.lenient().when(payment.sequenceType()).thenReturn(SequenceTypeResponse.of(sequenceType));
        return payment;
    }

    private static PaymentResponse paidFirstPaymentWithAmount() {
        PaymentResponse payment = mockPayment(PAYMENT_ID, "paid", CUSTOMER_ID, "first");
        Mockito.lenient().when(payment.metadata()).thenReturn(JsonNullable.of(Metadata.of(SubscriptionTier.SMALL.name())));
        Mockito.lenient().when(payment.amount()).thenReturn(Amount.builder().value("9.99").currency("EUR").build());
        return payment;
    }

    private static SubscriptionResponse mockSubscriptionResponse(String id, String nextPaymentDate) {
        return SubscriptionTestUtils.mockSubscriptionResponse(id, nextPaymentDate);
    }
}