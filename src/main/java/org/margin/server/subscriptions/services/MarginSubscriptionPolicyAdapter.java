package org.margin.server.subscriptions.services;

import org.margin.server.social.api.MarginSubscriptionPolicy;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.springframework.stereotype.Component;

@Component
public class MarginSubscriptionPolicyAdapter implements MarginSubscriptionPolicy {

    private final SubscriptionService subscriptionService;
    private final SubscriptionValidationService subscriptionValidationService;

    public MarginSubscriptionPolicyAdapter(SubscriptionService subscriptionService,
                                           SubscriptionValidationService subscriptionValidationService) {
        this.subscriptionService = subscriptionService;
        this.subscriptionValidationService = subscriptionValidationService;
    }

    @Override
    public void onMarginCreated(Long marginId) {
        subscriptionService.createSubscriptionForMargin(marginId, SubscriptionTier.FREE);
    }

    @Override
    public void validateAddMarginMember(Long marginId) {
        subscriptionValidationService.validateAddMarginMember(marginId);
    }

    @Override
    public void notifyIfApproachingMemberLimit(Long marginId) {
        subscriptionValidationService.notifyIfApproachingMemberLimit(marginId);
    }
}
