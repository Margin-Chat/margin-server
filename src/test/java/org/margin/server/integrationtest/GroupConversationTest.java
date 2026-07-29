package org.margin.server.integrationtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.ConversationTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationInviteStatus;
import org.margin.server.social.conversation.models.dtos.ConversationDTO;
import org.margin.server.social.conversation.models.dtos.DirectConversationDTO;
import org.margin.server.social.conversation.models.dtos.GroupConversationDTO;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.models.User;
import org.margin.server.social.conversation.models.dtos.ConversationInvitePayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GroupConversationTest extends MarginTestRunner {

    @Autowired
    private MessageService messageService;

    private User creator;
    private User memberA;
    private User memberB;

    @BeforeEach
    void setUp() {
        creator = UserTestUtils.createUser("creator", "creator@margin.chat");
        memberA = UserTestUtils.createUser("memberA", "memberA@margin.chat");
        memberB = UserTestUtils.createUser("memberB", "memberB@margin.chat");
    }

    @Test
    void createGroup_returnsGroupConversationDTO() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat", "memberB@margin.chat"), "Test Group", false);

        assertNotNull(dto);
        assertInstanceOf(GroupConversationDTO.class, dto);
        GroupConversationDTO group = (GroupConversationDTO) dto;
        assertEquals("Test Group", group.name());
        assertFalse(group.encrypted());
        // Creator is the only ACCEPTED member in the returned DTO (invitees are PENDING)
        assertTrue(group.memberIds().contains(creator.getId()));
    }

    @Test
    void createEncryptedGroup_setsEncryptedFlag() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "Secret Group", true);

        GroupConversationDTO group = (GroupConversationDTO) dto;
        assertTrue(group.encrypted());
    }

    @Test
    void createGroup_invitedMembersSeePendingInvite() {
        ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat", "memberB@margin.chat"), "My Group", false);

        List<ConversationInvitePayload> pendingA = ConversationTestUtils.getPendingInvites(memberA);
        List<ConversationInvitePayload> pendingB = ConversationTestUtils.getPendingInvites(memberB);

        assertEquals(1, pendingA.size());
        assertEquals(1, pendingB.size());
        assertInstanceOf(GroupConversationDTO.class, pendingA.getFirst().conversation());
        assertEquals("My Group", ((GroupConversationDTO) pendingA.getFirst().conversation()).name());
        assertEquals(creator.getId(), pendingA.getFirst().fromUser().id());
    }

    @Test
    void createEncryptedGroup_inviteShowsEncryptedFlag() {
        ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "Secret Group", true);

        List<ConversationInvitePayload> pending = ConversationTestUtils.getPendingInvites(memberA);

        assertEquals(1, pending.size());
        GroupConversationDTO groupDTO = (GroupConversationDTO) pending.getFirst().conversation();
        assertTrue(groupDTO.encrypted());
    }

    @Test
    void creator_hasNoPendingInvites() {
        ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "My Group", false);

        List<ConversationInvitePayload> pending = ConversationTestUtils.getPendingInvites(creator);

        assertTrue(pending.isEmpty());
    }

    @Test
    void acceptGroupInvite_removesFromPending() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "My Group", false);

        ConversationTestUtils.acceptInvite(dto.id(), memberA);

        List<ConversationInvitePayload> pending = ConversationTestUtils.getPendingInvites(memberA);
        assertTrue(pending.isEmpty());
    }

    @Test
    void declineGroupInvite_removesFromPending() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "My Group", false);

        ConversationTestUtils.declineInvite(dto.id(), memberA);

        List<ConversationInvitePayload> pending = ConversationTestUtils.getPendingInvites(memberA);
        assertTrue(pending.isEmpty());
    }

    @Test
    void createGroup_noInvitees_creatorOnly() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of(), "Solo Group", false);

        GroupConversationDTO group = (GroupConversationDTO) dto;
        assertEquals(1, group.memberIds().size());
        assertTrue(group.memberIds().contains(creator.getId()));
    }

    @Test
    void createGroup_withUnknownEmail_throws() {
        assertThrows(Exception.class, () ->
                ConversationTestUtils.createGroupConversation(
                        creator, List.of("nobody@margin.chat"), "Bad Group", false));
    }

    @Test
    void getMemberPublicKeys_returnsMembersWithKeys() {
        // Members without uploaded keys — map should be empty or only include those who have keys
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of(), "Key Group", false);

        var keys = ConversationTestUtils.getMemberPublicKeys(dto.id(), creator);
        // No keys uploaded in test setup — result should be empty map, not an error
        assertNotNull(keys);
    }

    @Test
    void pendingMember_notIncludedInMessageRecipients() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "My Group", false);

        Set<Long> recipientIds = new java.util.HashSet<>(ConversationTestUtils.getConversationMembers(dto.id()));

        assertFalse(recipientIds.contains(memberA.getId()),
                "PENDING member must not receive messages");
        assertTrue(recipientIds.contains(creator.getId()),
                "ACCEPTED creator must receive messages");
    }

    @Test
    void acceptedMember_includedInMessageRecipients() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "My Group", false);
        ConversationTestUtils.acceptInvite(dto.id(), memberA);

        Set<Long> recipientIds = new java.util.HashSet<>(ConversationTestUtils.getConversationMembers(dto.id()));

        assertTrue(recipientIds.contains(memberA.getId()),
                "ACCEPTED member must receive messages");
    }

    @Test
    void declineGroupInvite_userNoLongerSeesGroupInConversationList() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "My Group", false);

        ConversationTestUtils.declineInvite(dto.id(), memberA);

        List<ConversationDTO> conversations = ConversationTestUtils.getUserConversations(memberA);
        boolean groupStillVisible = conversations.stream().anyMatch(c -> c.id().equals(dto.id()));
        assertFalse(groupStillVisible, "Declined user should no longer see the group in their conversation list");
    }

    @Test
    void declineGroupInvite_userNoLongerAppearsInMemberList() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "My Group", false);

        ConversationTestUtils.declineInvite(dto.id(), memberA);

        List<ConversationDTO> creatorConversations = ConversationTestUtils.getUserConversations(creator);
        GroupConversationDTO group = creatorConversations.stream()
                .filter(c -> c.id().equals(dto.id()))
                .map(c -> (GroupConversationDTO) c)
                .findFirst()
                .orElseThrow();
        assertFalse(group.memberIds().contains(memberA.getId()),
                "Declined user should not appear in the group member list");
    }

    @Test
    void declineGroupInvite_secondDeclineThrows() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "My Group", false);

        ConversationTestUtils.declineInvite(dto.id(), memberA);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                ConversationTestUtils.declineInvite(dto.id(), memberA));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getMemberPublicKeys_nonMember_throws() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of(), "Key Group", false);

        User outsider = UserTestUtils.createUser("outsider", "outsider@margin.chat");

        assertThrows(Exception.class, () ->
                ConversationTestUtils.getMemberPublicKeys(dto.id(), outsider));
    }

    @Test
    void doubleInvite_returns409() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of(), "Group", false);
        ConversationTestUtils.addMemberToConversation(dto.id(), memberA.getId(), creator);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ConversationTestUtils.addMemberToConversation(dto.id(), memberA.getId(), creator));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void selfInvite_returns400() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of(), "Group", false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ConversationTestUtils.addMemberToConversation(dto.id(), creator.getId(), creator));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void pendingMember_cannotAddMembers_returns403() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "Group", false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ConversationTestUtils.addMemberToConversation(dto.id(), memberB.getId(), memberA));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void pendingMember_cannotReadMessages_returns403() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "Group", false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ConversationTestUtils.getGroupMessages(dto.id(), memberA));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void pendingMember_cannotFetchPublicKeys_returns403() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "Group", false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ConversationTestUtils.getMemberPublicKeys(dto.id(), memberA));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void directDecline_blocksReinvite() {
        // Use a lowercase email — userService.getByEmail lowercases before lookup
        User alice = UserTestUtils.createUser("alice", "alice@margin.chat");
        DirectConversationDTO invite = ConversationTestUtils.sendInvite(creator, "alice@margin.chat");
        ConversationTestUtils.declineInvite(invite.id(), alice);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> ConversationTestUtils.sendInvite(creator, "alice@margin.chat"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void groupDeclineThenReinvite_succeeds() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of("memberA@margin.chat"), "Group", false);
        ConversationTestUtils.declineInvite(dto.id(), memberA);

        assertDoesNotThrow(() ->
                ConversationTestUtils.addMemberToConversation(dto.id(), memberA.getId(), creator));

        List<ConversationInvitePayload> pending = ConversationTestUtils.getPendingInvites(memberA);
        assertEquals(1, pending.size());
    }

    @Test
    void editMessage_byNonAuthor_returns403() {
        ConversationDTO dto = ConversationTestUtils.createGroupConversation(
                creator, List.of(), "Group", false);
        Conversation conversation = ConversationTestUtils.getConversationById(dto.id());

        MessageResult result = messageService.createMessageForUsers(creator, conversation, "hello", List.of());
        Long messageId = result.message().id();

        User outsider = UserTestUtils.createUser("outsider", "outsider@margin.chat");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.editMessage(outsider, dto.id(), messageId, "tampered"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
