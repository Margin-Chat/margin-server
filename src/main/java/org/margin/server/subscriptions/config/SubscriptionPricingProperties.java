package org.margin.server.subscriptions.config;

import org.margin.server.subscriptions.models.SubscriptionTier;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

@ConfigurationProperties(prefix = "subscriptions")
public record SubscriptionPricingProperties(
        String currency,
        Map<SubscriptionTier, TierConfig> tiers
) {
    public record TierConfig(
            BigDecimal price,
            int maxMembers,
            int maxStorageGb,
            int maxCallParticipants
    ) {}
}