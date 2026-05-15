package org.margin.server.subscriptions.models.dtos;

import org.margin.server.subscriptions.models.SubscriptionTier;

import java.math.BigDecimal;

public record TierPriceDTO(
        SubscriptionTier tier,
        BigDecimal price,
        String currency
) {
}