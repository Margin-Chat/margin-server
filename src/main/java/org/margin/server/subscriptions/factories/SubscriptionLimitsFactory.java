package org.margin.server.subscriptions.factories;

import org.margin.server.subscriptions.config.SubscriptionPricingProperties;
import org.margin.server.subscriptions.entities.SubscriptionLimits;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionLimitsFactory {

    private final SubscriptionPricingProperties pricingProperties;

    public SubscriptionLimitsFactory(SubscriptionPricingProperties pricingProperties) {
        this.pricingProperties = pricingProperties;
    }

    public SubscriptionLimits forTier(SubscriptionTier tier) {
        SubscriptionPricingProperties.TierConfig config = pricingProperties.tiers().get(tier);
        if (config == null) {
            throw new IllegalArgumentException("No tier configuration found for: " + tier);
        }
        SubscriptionLimits limits = new SubscriptionLimits();
        limits.setMaxMembers(config.maxMembers());
        limits.setMaxStorageGb(config.maxStorageGb());
        limits.setMaxCallParticipants(config.maxCallParticipants());
        return limits;
    }
}