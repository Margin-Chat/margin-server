package org.margin.server.subscriptions.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.events.MemberLimitWarningEvent;
import org.margin.server.social.api.MarginDirectory;
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
    private final MarginDirectory marginDirectory;

    public SubscriptionValidationService(SubscriptionService subscriptionService,
                                         ApplicationEventPublisher eventPublisher,
                                         MarginDirectory marginDirectory) {
        this.subscriptionService = subscriptionService;
        this.eventPublisher = eventPublisher;
        this.marginDirectory = marginDirectory;
    }

    public Subscription getSubscriptionForMargin(Long marginId) {
        return subscriptionService.getByMarginId(marginId);
    }

    public void validateAddMarginMember(Long marginId) {
        Subscription subscription = getSubscriptionForMargin(marginId);
        if (marginDirectory.summaryOf(marginId).memberCount() >= subscription.getLimits().getMaxMembers()) {
            throw new SubscriptionLimitExceededException(
                    "Maximum number of margin members exceeded",
                    subscription.getTier(),
                    LimitType.MEMBERS
            );
        }
    }

    public void notifyIfApproachingMemberLimit(Long marginId) {
        Subscription subscription = getSubscriptionForMargin(marginId);
        int max = subscription.getLimits().getMaxMembers();
        int thresholdCount = (int) Math.ceil(max * WARNING_THRESHOLD);
        if (marginDirectory.summaryOf(marginId).memberCount() != thresholdCount) {
            return;
        }

        List<Long> recipients = marginDirectory.adminUserIdsOf(marginId);

        try {
            eventPublisher.publishEvent(new MemberLimitWarningEvent(recipients, marginId));
        } catch (Exception e) {
            log.warn("Failed to send limit warning for margin {}", marginId, e);
        }
    }

    public SubscriptionTier tierForMargin(Long marginId) {
        return getSubscriptionForMargin(marginId).getTier();
    }

    public int getMaxCallParticipants(Long marginId) {
        return getSubscriptionForMargin(marginId).getLimits().getMaxCallParticipants();
    }

    public void validateStorageQuota(Long marginId, long usedBytes, long newFileBytes) {
        Subscription subscription = getSubscriptionForMargin(marginId);
        long maxBytes = subscription.getLimits().getMaxStorageGb() * BYTES_PER_GB;
        if (usedBytes + newFileBytes > maxBytes) {
            throw new SubscriptionLimitExceededException(
                    "Storage quota exceeded",
                    subscription.getTier(),
                    LimitType.STORAGE
            );
        }
    }

    public int validateChannelVoiceJoin(Long marginId, int currentParticipants) {
        Subscription subscription = getSubscriptionForMargin(marginId);
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
