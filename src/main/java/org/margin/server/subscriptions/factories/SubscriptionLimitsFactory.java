package org.margin.server.subscriptions.factories;

import org.margin.server.subscriptions.entities.SubscriptionLimits;
import org.margin.server.subscriptions.models.SubscriptionTier;

public class SubscriptionLimitsFactory {
    public static SubscriptionLimits forTier(SubscriptionTier tier) {
        SubscriptionLimits limits = new SubscriptionLimits();
        switch (tier) {
            case FREE -> {
                limits.setMaxMembers(25);
                limits.setMaxStorageGb(5);
                limits.setMaxCallParticipants(10);
            }
            case SMALL -> {
                limits.setMaxMembers(50);
                limits.setMaxStorageGb(50);
                limits.setMaxCallParticipants(25);
            }
            case MEDIUM -> {
                limits.setMaxMembers(200);
                limits.setMaxStorageGb(200);
                limits.setMaxCallParticipants(50);
            }
            case CUSTOM -> {
                // Set manually per negotiation
                limits.setMaxMembers(Integer.MAX_VALUE);
                limits.setMaxStorageGb(Integer.MAX_VALUE);
                limits.setMaxCallParticipants(Integer.MAX_VALUE);
            }
        }
        return limits;
    }
}