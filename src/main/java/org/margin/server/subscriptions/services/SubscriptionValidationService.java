package org.margin.server.subscriptions.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.notifications.NotificationType;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.exceptions.SubscriptionLimitExceededException;
import org.margin.server.subscriptions.models.LimitType;
import org.margin.server.subscriptions.repositories.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
public class SubscriptionValidationService {
    private static final double WARNING_THRESHOLD = 0.9;

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationService notificationService;

    public SubscriptionValidationService(SubscriptionRepository subscriptionRepository,
                                         NotificationService notificationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.notificationService = notificationService;
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

    public void notifyIfApproachingMemberLimit(Margin margin) {
        Subscription subscription = getSubscriptionForMargin(margin);
        int max = subscription.getLimits().getMaxMembers();
        int thresholdCount = (int) Math.ceil(max * WARNING_THRESHOLD);
        if (margin.getMembers().size() != thresholdCount) {
            return;
        }

        margin.getMembers().stream()
                .filter(m -> m.getRole() == MarginRole.OWNER || m.getRole() == MarginRole.ADMIN)
                .map(MarginMember::getUser)
                .forEach(user -> {
                    try {
                        notificationService.createForUsers(
                                Collections.singletonList(user),
                                null,
                                NotificationType.SUBSCRIPTION_LIMIT_WARNING,
                                null,
                                margin.getId()
                        );
                    } catch (Exception e) {
                        log.warn("Failed to send limit warning to user {} for margin {}",
                                user.getId(), margin.getId(), e);
                    }
                });
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
