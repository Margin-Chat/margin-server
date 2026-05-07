package org.margin.server.subscriptions.exceptions;

import lombok.Getter;
import org.margin.server.subscriptions.models.LimitType;
import org.margin.server.subscriptions.models.SubscriptionTier;

@Getter
public class SubscriptionLimitExceededException extends RuntimeException {
    private final SubscriptionTier tier;
    private final LimitType limit;

    public SubscriptionLimitExceededException(String message, SubscriptionTier tier, LimitType limit) {
        super(message);
        this.tier = tier;
        this.limit = limit;
    }
}
