package org.margin.server.notifications.listeners;

import org.margin.server.notifications.NotificationType;
import org.margin.server.notifications.events.AnnouncementCreatedEvent;
import org.margin.server.notifications.events.MemberLimitWarningEvent;
import org.margin.server.notifications.events.MissedCallEvent;
import org.margin.server.notifications.events.SubscriptionStatusChangedEvent;
import org.margin.server.notifications.events.UserAddedToMarginEvent;
import org.margin.server.notifications.events.UserInvitedToMarginEvent;
import org.margin.server.notifications.services.NotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class NotificationEventListener {

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener
    public void onMissedCall(MissedCallEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getRecipient()),
                event.getCaller(),
                NotificationType.MISSED_CALL,
                event.getCallId(),
                null);
    }

    @EventListener
    public void onUserAddedToMargin(UserAddedToMarginEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getAddedUser()),
                event.getAddingUser(),
                NotificationType.ADDED_TO_MARGIN,
                null,
                event.getMarginId());
    }

    @EventListener
    public void onUserInvitedToMargin(UserInvitedToMarginEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getInvitedUser()),
                event.getInvitedBy(),
                NotificationType.INVITED_TO_MARGIN,
                event.getInviteId(),
                event.getMarginId());
    }

    @EventListener
    public void onAnnouncementCreated(AnnouncementCreatedEvent event) {
        notificationService.createForUsers(
                event.getMembers(),
                event.getAuthor(),
                NotificationType.ANNOUNCEMENT,
                event.getAnnouncementId(),
                event.getMarginId());
    }

    @EventListener
    public void onMemberLimitWarning(MemberLimitWarningEvent event) {
        notificationService.createForUsers(
                event.getRecipients(),
                null,
                NotificationType.SUBSCRIPTION_LIMIT_WARNING,
                null,
                event.getMarginId());
    }

    @EventListener
    public void onSubscriptionStatusChanged(SubscriptionStatusChangedEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getOwner()),
                null,
                event.getType(),
                null,
                event.getMarginId());
    }
}