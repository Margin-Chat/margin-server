package org.margin.server.subscriptions.models.dtos;

import org.margin.server.subscriptions.models.SubscriptionStatus;
import org.margin.server.subscriptions.models.SubscriptionTier;

import java.time.Instant;

public record SubscriptionDTO(
        SubscriptionTier tier,
        SubscriptionStatus status,
        SubscriptionLimitsDTO limits,
        int currentMembers,
        Instant trialEndsAt,
        Instant currentPeriodEnd,
        boolean hasPendingPayment,
        SubscriptionTier pendingTier
) {
}
