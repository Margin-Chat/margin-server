package org.margin.server.subscriptions.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.subscriptions.events.MemberLimitWarningEvent;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.models.MarginRole;
import org.margin.server.subscriptions.entities.Subscription;
import org.margin.server.subscriptions.exceptions.SubscriptionLimitExceededException;
import org.margin.server.subscriptions.models.LimitType;
import org.margin.server.users.models.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SubscriptionValidationService {
    private static final double WARNING_THRESHOLD = 0.9;

    private static final long BYTES_PER_GB = 1024L * 1024 * 1024;

    private final SubscriptionService subscriptionService;
    private final ApplicationEventPublisher eventPublisher;

    public SubscriptionValidationService(SubscriptionService subscriptionService,
                                         ApplicationEventPublisher eventPublisher) {
        this.subscriptionService = subscriptionService;
        this.eventPublisher = eventPublisher;
    }

    public Subscription getSubscriptionForMargin(Margin margin) {
        return subscriptionService.getByMargin(margin);
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

        List<Long> recipients = margin.getMembers().stream()
                .filter(m -> m.getRole() == MarginRole.OWNER || m.getRole() == MarginRole.ADMIN)
                .map(m -> m.getUser().getId())
                .toList();

        try {
            eventPublisher.publishEvent(new MemberLimitWarningEvent(recipients, margin.getId()));
        } catch (Exception e) {
            log.warn("Failed to send limit warning for margin {}", margin.getId(), e);
        }
    }

    public int getMaxCallParticipants(Channel channel) {
        Margin margin = channel.getSpace().getMargin();
        return getSubscriptionForMargin(margin).getLimits().getMaxCallParticipants();
    }

    public void validateStorageQuota(Margin margin, long usedBytes, long newFileBytes) {
        Subscription subscription = getSubscriptionForMargin(margin);
        long maxBytes = subscription.getLimits().getMaxStorageGb() * BYTES_PER_GB;
        if (usedBytes + newFileBytes > maxBytes) {
            throw new SubscriptionLimitExceededException(
                    "Storage quota exceeded",
                    subscription.getTier(),
                    LimitType.STORAGE
            );
        }
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
