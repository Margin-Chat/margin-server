package org.margin.server.notifications.listeners;

import org.margin.server.shared.notifications.NotificationType;
import org.margin.server.social.announcements.events.AnnouncementCreatedEvent;
import org.margin.server.subscriptions.events.MemberLimitWarningEvent;
import org.margin.server.social.calls.events.MissedCallEvent;
import org.margin.server.subscriptions.events.SubscriptionStatusChangedEvent;
import org.margin.server.social.margin.events.UserAddedToMarginEvent;
import org.margin.server.social.margin.events.UserInvitedToMarginEvent;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.social.conversation.events.ConversationInviteAcceptedEvent;
import org.margin.server.social.conversation.events.ConversationInviteDeclinedEvent;
import org.margin.server.social.conversation.events.ConversationInviteEvent;
import org.margin.server.social.api.ConversationType;
import org.margin.server.social.api.MessageDirectory;
import org.margin.server.social.messages.events.MessageSentEvent;
import org.margin.server.social.messages.events.ReactionAddedEvent;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.messages.models.dtos.MessageReactionDTO;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageDirectory messageDirectory;

    public NotificationEventListener(NotificationService notificationService, UserService userService,
                                     MessageDirectory messageDirectory) {
        this.notificationService = notificationService;
        this.userService = userService;
        this.messageDirectory = messageDirectory;
    }

    @EventListener
    public void onMissedCall(MissedCallEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getRecipientId()),
                event.getCallerId(),
                NotificationType.MISSED_CALL,
                event.getCallId(),
                null);
    }

    @EventListener
    public void onUserAddedToMargin(UserAddedToMarginEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getAddedUserId()),
                event.getAddingUserId(),
                NotificationType.ADDED_TO_MARGIN,
                null,
                event.getMarginId());
    }

    @EventListener
    public void onUserInvitedToMargin(UserInvitedToMarginEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getInvitedUserId()),
                event.getInvitedByUserId(),
                NotificationType.INVITED_TO_MARGIN,
                event.getInviteId(),
                event.getMarginId());
    }

    @EventListener
    public void onConversationInvite(ConversationInviteEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getRecipientId()),
                event.getSender().id(),
                NotificationType.CONVERSATION_INVITE,
                event.getConversation().id(),
                null);
    }

    @EventListener
    public void onConversationInviteAccepted(ConversationInviteAcceptedEvent event) {
        notificationService.markSeenByReference(event.getAcceptedBy().id(), event.getConversation().id());
    }

    @EventListener
    public void onConversationInviteDeclined(ConversationInviteDeclinedEvent event) {
        notificationService.markSeenByReference(event.getDeclinedBy().id(), event.getConversationId());
    }

    @EventListener
    public void onReactionAdded(ReactionAddedEvent event) {
        MessageReactionDTO reaction = event.getReaction();
        MessageDirectory.MessageContext context = messageDirectory.contextOf(reaction.messageId());

        if (context.authorId().equals(reaction.userId())) {
            return;
        }

        Long reactorId = event.getRecipientIds().stream()
                .filter(id -> id.equals(reaction.userId()))
                .findFirst()
                .orElse(null);

        notificationService.createForUsers(
                Collections.singletonList(context.authorId()),
                reactorId,
                NotificationType.MESSAGE_REACTION,
                reaction.messageId(),
                context.marginId(),
                context.conversationId());
    }

    @EventListener
    public void onMessageSent(MessageSentEvent event) {
        MessageDTO message = event.getMessage();
        if (message.conversationType() != ConversationType.THREAD) {
            return;
        }

        List<Long> followerIds = messageDirectory.threadFollowerIds(message.conversationId());
        Long senderId = event.getRecipientIds().stream()
                .filter(id -> id.equals(message.user().id()))
                .findFirst()
                .orElse(null);

        notificationService.createOrCollapseThreadReply(
                followerIds,
                senderId,
                message.id(),
                message.marginId(),
                message.conversationId());
    }

    @EventListener
    public void onAnnouncementCreated(AnnouncementCreatedEvent event) {
        notificationService.createForUsers(
                event.getMemberIds(),
                event.getAuthor().id(),
                NotificationType.ANNOUNCEMENT,
                event.getAnnouncementId(),
                event.getMarginId());
    }

    @EventListener
    public void onMemberLimitWarning(MemberLimitWarningEvent event) {
        notificationService.createForUsers(
                event.getRecipientIds(),
                null,
                NotificationType.SUBSCRIPTION_LIMIT_WARNING,
                null,
                event.getMarginId());
    }

    @EventListener
    public void onSubscriptionStatusChanged(SubscriptionStatusChangedEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getOwnerId()),
                null,
                event.getType(),
                null,
                event.getMarginId());
    }
}