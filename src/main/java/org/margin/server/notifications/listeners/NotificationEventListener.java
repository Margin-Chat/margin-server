package org.margin.server.notifications.listeners;

import org.margin.server.notifications.NotificationType;
import org.margin.server.notifications.events.AnnouncementCreatedEvent;
import org.margin.server.notifications.events.MemberLimitWarningEvent;
import org.margin.server.notifications.events.MissedCallEvent;
import org.margin.server.notifications.events.SubscriptionStatusChangedEvent;
import org.margin.server.notifications.events.UserAddedToMarginEvent;
import org.margin.server.notifications.events.UserInvitedToMarginEvent;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.social.conversation.events.ConversationInviteAcceptedEvent;
import org.margin.server.social.conversation.events.ConversationInviteDeclinedEvent;
import org.margin.server.social.conversation.events.ConversationInviteEvent;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.messages.events.ReactionAddedEvent;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.dtos.MessageReactionDTO;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final UserService userService;
    private final MessageService messageService;

    public NotificationEventListener(NotificationService notificationService, UserService userService,
                                     MessageService messageService) {
        this.notificationService = notificationService;
        this.userService = userService;
        this.messageService = messageService;
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
    public void onConversationInvite(ConversationInviteEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(userService.getById(event.getRecipientId())),
                event.getSender(),
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
        Message message = messageService.getById(reaction.messageId());
        User author = message.getFromUser();

        if (author.getId().equals(reaction.userId())) {
            return;
        }

        User reactor = event.getRecipients().stream()
                .filter(u -> u.getId().equals(reaction.userId()))
                .findFirst()
                .orElse(null);

        Conversation conversation = message.getConversation();
        Long marginId = conversation.getChannel() == null ? null
                : conversation.getChannel().getSpace().getMargin().getId();

        notificationService.createForUsers(
                Collections.singletonList(author),
                reactor,
                NotificationType.MESSAGE_REACTION,
                reaction.messageId(),
                marginId);
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