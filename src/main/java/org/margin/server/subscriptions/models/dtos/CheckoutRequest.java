package org.margin.server.subscriptions.models.dtos;

import org.margin.server.subscriptions.models.SubscriptionTier;

public record CheckoutRequest(
        Long marginId,
        SubscriptionTier tier
) {
}