package org.margin.server.unittest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.notifications.NotificationType;
import org.margin.server.notifications.listeners.NotificationEventListener;
import org.margin.server.notifications.services.NotificationService;
import org.margin.server.social.conversation.events.ConversationInviteAcceptedEvent;
import org.margin.server.social.conversation.events.ConversationInviteDeclinedEvent;
import org.margin.server.social.conversation.events.ConversationInviteEvent;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationInviteStatus;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.models.dtos.DirectConversationDTO;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.messages.events.ReactionAddedEvent;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.dtos.MessageReactionDTO;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.social.space.models.Space;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.services.UserService;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private UserService userService;
    @Mock
    private MessageService messageService;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void onConversationInvite_createsNotificationForRecipient() {
        User sender = createUser(1L, "Sender");
        User recipient = createUser(2L, "Recipient");
        when(userService.getById(recipient.getId())).thenReturn(recipient);

        DirectConversationDTO conversation = new DirectConversationDTO(
                42L, Instant.now(), sender.getId(), null, ConversationInviteStatus.PENDING, false);

        listener.onConversationInvite(new ConversationInviteEvent(conversation, sender, recipient.getId()));

        verify(notificationService).createForUsers(
                List.of(recipient), sender, NotificationType.CONVERSATION_INVITE, 42L, null);
    }

    @Test
    void onConversationInviteAccepted_marksNotificationSeenForAccepter() {
        UserDTO acceptedBy = new UserDTO(3L, "Accepter", null, Instant.now(), Instant.now(), false);
        DirectConversationDTO conversation = new DirectConversationDTO(
                42L, Instant.now(), 1L, null, ConversationInviteStatus.ACCEPTED, false);

        listener.onConversationInviteAccepted(new ConversationInviteAcceptedEvent(conversation, acceptedBy, 1L));

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> referenceIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(notificationService).markSeenByReference(userIdCaptor.capture(), referenceIdCaptor.capture());

        assertThat(userIdCaptor.getValue()).isEqualTo(3L);
        assertThat(referenceIdCaptor.getValue()).isEqualTo(42L);
    }

    @Test
    void onConversationInviteDeclined_marksNotificationSeenForDecliner() {
        UserDTO declinedBy = new UserDTO(3L, "Decliner", null, Instant.now(), Instant.now(), false);

        listener.onConversationInviteDeclined(new ConversationInviteDeclinedEvent(42L, declinedBy, 1L));

        verify(notificationService).markSeenByReference(3L, 42L);
    }

    @Test
    void onReactionAdded_createsNotificationForAuthor_andResolvesMarginIdForChannelMessage() {
        User author = createUser(2L, "Author");
        User reactor = createUser(3L, "Reactor");

        Margin margin = new Margin();
        margin.setId(99L);
        Space space = new Space();
        space.setMargin(margin);
        Channel channel = new Channel();
        channel.setSpace(space);
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.CHANNEL);
        conversation.setChannel(channel);

        Message message = new Message();
        message.setId(10L);
        message.setFromUser(author);
        message.setConversation(conversation);
        when(messageService.getById(10L)).thenReturn(message);

        MessageReactionDTO reaction = new MessageReactionDTO(1L, 10L, 5L, reactor.getId(), "Reactor", "👍");

        listener.onReactionAdded(new ReactionAddedEvent(reaction, List.of(author, reactor), ConversationType.CHANNEL));

        verify(notificationService).createForUsers(
                List.of(author), reactor, NotificationType.MESSAGE_REACTION, 10L, 99L);
    }

    @Test
    void onReactionAdded_doesNotNotify_whenAuthorReactsToOwnMessage() {
        User author = createUser(2L, "Author");

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);

        Message message = new Message();
        message.setId(10L);
        message.setFromUser(author);
        message.setConversation(conversation);
        when(messageService.getById(10L)).thenReturn(message);

        MessageReactionDTO reaction = new MessageReactionDTO(1L, 10L, 5L, author.getId(), "Author", "👍");

        listener.onReactionAdded(new ReactionAddedEvent(reaction, List.of(author), ConversationType.DIRECT));

        verify(notificationService, never()).createForUsers(any(), any(), any(), any(), any());
    }
}
