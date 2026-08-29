package org.margin.server.subscriptions.exceptions;

import lombok.Getter;
import org.margin.server.shared.exceptions.DomainException;
import org.margin.server.subscriptions.models.LimitType;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class SubscriptionLimitExceededException extends DomainException {
    private final SubscriptionTier tier;
    private final LimitType limit;

    public SubscriptionLimitExceededException(String message, SubscriptionTier tier, LimitType limit) {
        super(HttpStatus.PAYMENT_REQUIRED, message);
        this.tier = tier;
        this.limit = limit;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("tier", tier, "limit", limit);
    }
}
