package org.margin.server.subscriptions.services;

import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.exceptions.SubscriptionLimitExceededException;
import org.margin.server.subscriptions.models.LimitType;
import org.margin.server.subscriptions.repositories.SubscriptionRepository;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionValidationService {
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionValidationService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public Subscription getSubscriptionForMargin(Margin margin) {
        return subscriptionRepository.findByMargin(margin).orElseThrow();
    }

    public void validateAddMarginMember(Margin margin) {
        Subscription subscription = getSubscriptionForMargin(margin);
        if (margin.getMembers().size() >= subscription.getLimits().getMaxMembers()) {
            throw new SubscriptionLimitExceededException(
                    "Maximum number of margin members exceeded",
                    subscription.getTier(),
                    LimitType.MEMBERS
            );
        }
    }

    public int getMaxCallParticipants(Channel channel) {
        Margin margin = channel.getSpace().getMargin();
        return getSubscriptionForMargin(margin).getLimits().getMaxCallParticipants();
    }

    public int validateChannelVoiceJoin(Channel channel, int currentParticipants) {
        Margin margin = channel.getSpace().getMargin();
        Subscription subscription = getSubscriptionForMargin(margin);
        int max = subscription.getLimits().getMaxCallParticipants();
        if (currentParticipants >= max) {
            throw new SubscriptionLimitExceededException(
                    "Maximum call participants exceeded",
                    subscription.getTier(),
                    LimitType.CALL_PARTICIPANTS
            );
        }
        return max;
    }
}
