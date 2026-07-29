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
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.messages.events.MessageSentEvent;
import org.margin.server.social.messages.events.ReactionAddedEvent;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.messages.models.dtos.MessageReactionDTO;
import org.margin.server.social.messages.services.MessageService;
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
    private final MessageService messageService;
    private final ConversationService conversationService;

    public NotificationEventListener(NotificationService notificationService, UserService userService,
                                     MessageService messageService, ConversationService conversationService) {
        this.notificationService = notificationService;
        this.userService = userService;
        this.messageService = messageService;
        this.conversationService = conversationService;
    }

    @EventListener
    public void onMissedCall(MissedCallEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getRecipient().getId()),
                event.getCaller().getId(),
                NotificationType.MISSED_CALL,
                event.getCallId(),
                null);
    }

    @EventListener
    public void onUserAddedToMargin(UserAddedToMarginEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getAddedUser().getId()),
                event.getAddingUser().getId(),
                NotificationType.ADDED_TO_MARGIN,
                null,
                event.getMarginId());
    }

    @EventListener
    public void onUserInvitedToMargin(UserInvitedToMarginEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getInvitedUser().getId()),
                event.getInvitedBy().getId(),
                NotificationType.INVITED_TO_MARGIN,
                event.getInviteId(),
                event.getMarginId());
    }

    @EventListener
    public void onConversationInvite(ConversationInviteEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getRecipientId()),
                event.getSender().getId(),
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
                Collections.singletonList(author.getId()),
                reactor == null ? null : reactor.getId(),
                NotificationType.MESSAGE_REACTION,
                reaction.messageId(),
                marginId,
                conversation.getId());
    }

    @EventListener
    public void onMessageSent(MessageSentEvent event) {
        MessageDTO message = event.getMessage();
        if (message.conversationType() != ConversationType.THREAD) {
            return;
        }

        List<User> followers = conversationService.getThreadFollowers(message.conversationId());
        User sender = event.getRecipients().stream()
                .filter(u -> u.getId().equals(message.user().id()))
                .findFirst()
                .orElse(null);

        notificationService.createOrCollapseThreadReply(
                followers.stream().map(User::getId).toList(),
                sender == null ? null : sender.getId(),
                message.id(),
                message.marginId(),
                message.conversationId());
    }

    @EventListener
    public void onAnnouncementCreated(AnnouncementCreatedEvent event) {
        notificationService.createForUsers(
                event.getMembers().stream().map(User::getId).toList(),
                event.getAuthor().getId(),
                NotificationType.ANNOUNCEMENT,
                event.getAnnouncementId(),
                event.getMarginId());
    }

    @EventListener
    public void onMemberLimitWarning(MemberLimitWarningEvent event) {
        notificationService.createForUsers(
                event.getRecipients().stream().map(User::getId).toList(),
                null,
                NotificationType.SUBSCRIPTION_LIMIT_WARNING,
                null,
                event.getMarginId());
    }

    @EventListener
    public void onSubscriptionStatusChanged(SubscriptionStatusChangedEvent event) {
        notificationService.createForUsers(
                Collections.singletonList(event.getOwner().getId()),
                null,
                event.getType(),
                null,
                event.getMarginId());
    }
}