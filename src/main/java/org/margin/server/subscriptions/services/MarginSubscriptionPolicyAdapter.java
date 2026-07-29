package org.margin.server.subscriptions.services;

import org.margin.server.social.margin.entities.Margin;
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
    public void onMarginCreated(Margin margin) {
        subscriptionService.createSubscriptionForMargin(margin, SubscriptionTier.FREE);
    }

    @Override
    public void validateAddMarginMember(Margin margin) {
        subscriptionValidationService.validateAddMarginMember(margin);
    }

    @Override
    public void notifyIfApproachingMemberLimit(Margin margin) {
        subscriptionValidationService.notifyIfApproachingMemberLimit(margin);
    }
}
