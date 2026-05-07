package org.margin.server.integrationtest.utils;

import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.entities.SubscriptionLimits;
import org.margin.server.subscriptions.repositories.SubscriptionRepository;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionTestUtils {
    private static SubscriptionRepository subscriptionRepository;
    private static SubscriptionValidationService subscriptionValidationService;
    private static ChannelService channelService;

    @Autowired
    public SubscriptionTestUtils(SubscriptionRepository subscriptionRepository,
                                 SubscriptionValidationService subscriptionValidationService,
                                 ChannelService channelService) {
        SubscriptionTestUtils.subscriptionRepository = subscriptionRepository;
        SubscriptionTestUtils.subscriptionValidationService = subscriptionValidationService;
        SubscriptionTestUtils.channelService = channelService;
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
}