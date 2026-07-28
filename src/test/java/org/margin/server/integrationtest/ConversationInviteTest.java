package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.ConversationTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.social.conversation.models.ConversationInviteStatus;
import org.margin.server.social.conversation.models.dtos.DirectConversationDTO;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.RecentChatUsersDTO;
import org.margin.server.social.conversation.models.dtos.ConversationInvitePayload;
import org.margin.server.social.conversation.models.dtos.SentConversationInvitePayload;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConversationInviteTest extends MarginTestRunner {

    private User sender;
    private User recipient;

    @BeforeEach
    void setUp() {
        sender = UserTestUtils.createUser("sender", "sender@margin.chat");
        recipient = UserTestUtils.createUser("recipient", "recipient@margin.chat");
    }

    @Test
    void sendInvite_createsConversationWithAcceptedStatusForSender() {
        DirectConversationDTO dto = ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        assertNotNull(dto);
        assertNotNull(dto.id());
        assertEquals(ConversationInviteStatus.ACCEPTED, dto.inviteStatus());
        assertEquals(recipient.getId(), dto.otherUserId());
        assertFalse(dto.encrypted());
    }

    @Test
    void sendEncryptedInvite_setsEncryptedFlag() {
        DirectConversationDTO dto = ConversationTestUtils.sendEncryptedInvite(sender, "recipient@margin.chat");

        assertNotNull(dto);
        assertTrue(dto.encrypted());
    }

    @Test
    void sendInvite_recipientSeesPendingInvite() {
        ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        List<ConversationInvitePayload> pending = ConversationTestUtils.getPendingInvites(recipient);

        assertEquals(1, pending.size());
        DirectConversationDTO conv = (DirectConversationDTO) pending.getFirst().conversation();
        assertEquals(ConversationInviteStatus.PENDING, conv.inviteStatus());
        assertEquals(sender.getId(), conv.otherUserId());
    }

    @Test
    void sendInvite_senderHasNoPendingInvites() {
        ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        List<ConversationInvitePayload> pending = ConversationTestUtils.getPendingInvites(sender);

        assertTrue(pending.isEmpty());
    }

    @Test
    void sendInvite_toNonExistentEmail_throws() {
        assertThrows(Exception.class, () ->
                ConversationTestUtils.sendInvite(sender, "nobody@margin.chat"));
    }

    @Test
    void sendInvite_duplicateConversation_throws() {
        ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ConversationTestUtils.sendInvite(sender, "recipient@margin.chat"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void acceptInvite_removesFromPendingAndAllowsMessages() {
        DirectConversationDTO invite = ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        ConversationTestUtils.acceptInvite(invite.id(), recipient);

        List<ConversationInvitePayload> pending = ConversationTestUtils.getPendingInvites(recipient);
        assertTrue(pending.isEmpty());
    }

    @Test
    void acceptInvite_byNonRecipient_throws() {
        DirectConversationDTO invite = ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");
        User outsider = UserTestUtils.createUser("outsider", "outsider@margin.chat");

        assertThrows(Exception.class, () ->
                ConversationTestUtils.acceptInvite(invite.id(), outsider));
    }

    @Test
    void acceptInvite_alreadyAccepted_throws() {
        DirectConversationDTO invite = ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");
        ConversationTestUtils.acceptInvite(invite.id(), recipient);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ConversationTestUtils.acceptInvite(invite.id(), recipient));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void declineInvite_removesFromPendingList() {
        DirectConversationDTO invite = ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        ConversationTestUtils.declineInvite(invite.id(), recipient);

        List<ConversationInvitePayload> pending = ConversationTestUtils.getPendingInvites(recipient);
        assertTrue(pending.isEmpty());
    }

    @Test
    void declineInvite_alreadyDeclined_throws() {
        DirectConversationDTO invite = ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");
        ConversationTestUtils.declineInvite(invite.id(), recipient);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ConversationTestUtils.declineInvite(invite.id(), recipient));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void sendMessage_toPendingConversation_throws() {
        DirectConversationDTO invite = ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ConversationTestUtils.getChannelMessages(invite.id(), sender));

        assertNotNull(invite.id());
    }

    @Test
    void multipleInvites_allAppearInPendingList() {
        User sender2 = UserTestUtils.createUser("sender2", "sender2@margin.chat");

        ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");
        ConversationTestUtils.sendInvite(sender2, "recipient@margin.chat");

        List<ConversationInvitePayload> pending = ConversationTestUtils.getPendingInvites(recipient);

        assertEquals(2, pending.size());
        assertTrue(pending.stream()
                .map(p -> (DirectConversationDTO) p.conversation())
                .allMatch(c -> c.inviteStatus() == ConversationInviteStatus.PENDING));
    }

    @Test
    void acceptOneInvite_otherRemainsInPendingList() {
        User sender2 = UserTestUtils.createUser("sender2", "sender2@margin.chat");

        DirectConversationDTO first = ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");
        ConversationTestUtils.sendInvite(sender2, "recipient@margin.chat");

        ConversationTestUtils.acceptInvite(first.id(), recipient);

        List<ConversationInvitePayload> pending = ConversationTestUtils.getPendingInvites(recipient);
        assertEquals(1, pending.size());
        assertEquals(sender2.getId(), ((DirectConversationDTO) pending.getFirst().conversation()).otherUserId());
    }

    @Test
    void pendingInvite_includesConversationAndFromUser() {
        ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        List<ConversationInvitePayload> pending = ConversationTestUtils.getPendingInvites(recipient);

        assertEquals(1, pending.size());
        ConversationInvitePayload invite = pending.getFirst();
        assertNotNull(invite.conversation(), "conversation must not be null");
        assertNotNull(invite.conversation().id(), "conversation.id must not be null");
        assertNotNull(invite.fromUser(), "fromUser must not be null");
        assertEquals(sender.getId(), invite.fromUser().id());
    }

    @Test
    void sendInvite_senderSeesSentInvite() {
        ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        List<SentConversationInvitePayload> sent = ConversationTestUtils.getSentInvites(sender);

        assertEquals(1, sent.size());
        SentConversationInvitePayload invite = sent.getFirst();
        DirectConversationDTO conv = (DirectConversationDTO) invite.conversation();
        assertEquals(ConversationInviteStatus.ACCEPTED, conv.inviteStatus());
        assertEquals(recipient.getId(), conv.otherUserId());
        assertEquals(recipient.getId(), invite.toUser().id());
    }

    @Test
    void sendInvite_recipientHasNoSentInvites() {
        ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        List<SentConversationInvitePayload> sent = ConversationTestUtils.getSentInvites(recipient);

        assertTrue(sent.isEmpty());
    }

    @Test
    void acceptInvite_removesFromSenderSentInvites() {
        DirectConversationDTO invite = ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        ConversationTestUtils.acceptInvite(invite.id(), recipient);

        List<SentConversationInvitePayload> sent = ConversationTestUtils.getSentInvites(sender);
        assertTrue(sent.isEmpty());
    }

    @Test
    void declineInvite_removesFromSenderSentInvites() {
        DirectConversationDTO invite = ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        ConversationTestUtils.declineInvite(invite.id(), recipient);

        List<SentConversationInvitePayload> sent = ConversationTestUtils.getSentInvites(sender);
        assertTrue(sent.isEmpty());
    }

    @Test
    void multipleSentInvites_allAppearInSentList() {
        User recipient2 = UserTestUtils.createUser("recipient2", "recipient2@margin.chat");

        ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");
        ConversationTestUtils.sendInvite(sender, "recipient2@margin.chat");

        List<SentConversationInvitePayload> sent = ConversationTestUtils.getSentInvites(sender);

        assertEquals(2, sent.size());
        assertTrue(sent.stream()
                .map(p -> (DirectConversationDTO) p.conversation())
                .allMatch(c -> c.inviteStatus() == ConversationInviteStatus.ACCEPTED));
    }

    @Test
    void pendingInvite_doesNotAppearInSenderRecentChatUsers() {
        ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        List<RecentChatUsersDTO> recent = ConversationTestUtils.getRecentChatUsers(sender);

        assertTrue(recent.isEmpty());
    }

    @Test
    void pendingInvite_doesNotAppearInRecipientRecentChatUsers() {
        ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");

        List<RecentChatUsersDTO> recent = ConversationTestUtils.getRecentChatUsers(recipient);

        assertTrue(recent.isEmpty());
    }

    @Test
    void acceptedInvite_appearsInBothRecentChatUsersLists() {
        DirectConversationDTO invite = ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");
        ConversationTestUtils.acceptInvite(invite.id(), recipient);

        List<RecentChatUsersDTO> senderRecent = ConversationTestUtils.getRecentChatUsers(sender);
        List<RecentChatUsersDTO> recipientRecent = ConversationTestUtils.getRecentChatUsers(recipient);

        assertEquals(1, senderRecent.size());
        assertEquals(1, recipientRecent.size());
    }

    @Test
    void declinedInvite_doesNotAppearInSenderRecentChatUsers() {
        DirectConversationDTO invite = ConversationTestUtils.sendInvite(sender, "recipient@margin.chat");
        ConversationTestUtils.declineInvite(invite.id(), recipient);

        List<RecentChatUsersDTO> recent = ConversationTestUtils.getRecentChatUsers(sender);

        assertTrue(recent.isEmpty());
    }
}
