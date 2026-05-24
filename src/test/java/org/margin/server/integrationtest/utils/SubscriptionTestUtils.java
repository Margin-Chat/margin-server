package org.margin.server.integrationtest.utils;

import com.mollie.mollie.models.components.SubscriptionResponse;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.entities.SubscriptionLimits;
import org.margin.server.subscriptions.factories.SubscriptionLimitsFactory;
import org.margin.server.subscriptions.models.SubscriptionStatus;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.repositories.SubscriptionRepository;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
import org.mockito.Mockito;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionTestUtils {
    private static SubscriptionRepository subscriptionRepository;
    private static SubscriptionValidationService subscriptionValidationService;
    private static ChannelService channelService;
    private static SubscriptionLimitsFactory subscriptionLimitsFactory;

    @Autowired
    public SubscriptionTestUtils(SubscriptionRepository subscriptionRepository,
                                 SubscriptionValidationService subscriptionValidationService,
                                 ChannelService channelService,
                                 SubscriptionLimitsFactory subscriptionLimitsFactory) {
        SubscriptionTestUtils.subscriptionRepository = subscriptionRepository;
        SubscriptionTestUtils.subscriptionValidationService = subscriptionValidationService;
        SubscriptionTestUtils.channelService = channelService;
        SubscriptionTestUtils.subscriptionLimitsFactory = subscriptionLimitsFactory;
    }

    public static Subscription getForMargin(Margin margin) {
        return subscriptionValidationService.getSubscriptionForMargin(margin);
    }

    public static int getMaxCallParticipantsForChannel(Long channelId) {
        Channel channel = channelService.getById(channelId);
        return subscriptionValidationService.getMaxCallParticipants(channel);
    }

    public static void overrideLimits(Margin margin, int maxMembers, int maxStorageGb, int maxCallParticipants) {
        Subscription subscription = subscriptionValidationService.getSubscriptionForMargin(margin);
        SubscriptionLimits limits = subscription.getLimits();
        limits.setMaxMembers(maxMembers);
        limits.setMaxStorageGb(maxStorageGb);
        limits.setMaxCallParticipants(maxCallParticipants);
        subscriptionRepository.save(subscription);
    }

    public static void setMollieCustomerId(Margin margin, String customerId) {
        Subscription subscription = subscriptionValidationService.getSubscriptionForMargin(margin);
        subscription.setMollieCustomerId(customerId);
        subscriptionRepository.save(subscription);
    }

    public static void setPendingPayment(Margin margin, String paymentId, SubscriptionTier tier) {
        Subscription subscription = subscriptionValidationService.getSubscriptionForMargin(margin);
        subscription.setPendingPaymentId(paymentId);
        subscription.setPendingTier(tier);
        subscriptionRepository.save(subscription);
    }

    public static SubscriptionResponse mockSubscriptionResponse(String id, String nextPaymentDate) {
        SubscriptionResponse sub = Mockito.mock(SubscriptionResponse.class);
        Mockito.lenient().when(sub.id()).thenReturn(id);
        Mockito.lenient().when(sub.nextPaymentDate()).thenReturn(JsonNullable.of(nextPaymentDate));
        return sub;
    }

    public static SubscriptionResponse mockSubscriptionResponseWithStartDate(String id, String startDate) {
        SubscriptionResponse sub = Mockito.mock(SubscriptionResponse.class);
        Mockito.lenient().when(sub.id()).thenReturn(id);
        Mockito.lenient().when(sub.nextPaymentDate()).thenReturn(JsonNullable.undefined());
        Mockito.lenient().when(sub.startDate()).thenReturn(startDate);
        return sub;
    }

    public static void setActiveSubscription(Margin margin, String customerId, String subscriptionId, SubscriptionTier tier) {
        Subscription subscription = subscriptionValidationService.getSubscriptionForMargin(margin);
        subscription.setMollieCustomerId(customerId);
        subscription.setSubscriptionId(subscriptionId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setTier(tier);
        SubscriptionLimits limits = subscription.getLimits();
        SubscriptionLimits desired = subscriptionLimitsFactory.forTier(tier);
        limits.setMaxMembers(desired.getMaxMembers());
        limits.setMaxStorageGb(desired.getMaxStorageGb());
        limits.setMaxCallParticipants(desired.getMaxCallParticipants());
        subscriptionRepository.save(subscription);
    }
}