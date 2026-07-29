package org.margin.server.integrationtest.utils;

import org.margin.server.social.conversation.controllers.ConversationController;
import static org.margin.server.integrationtest.utils.UserTestUtils.principalOf;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationMember;
import org.margin.server.social.api.ConversationType;
import org.margin.server.social.conversation.models.dtos.*;
import org.springframework.http.ResponseEntity;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.users.controllers.UserController;
import org.margin.server.users.models.User;
import org.margin.server.social.conversation.models.dtos.RecentChatUsersDTO;
import org.margin.server.social.conversation.models.dtos.ConversationInvitePayload;
import org.margin.server.social.conversation.models.dtos.SentConversationInvitePayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class ConversationTestUtils {

    private static ConversationRepository conversationRepository;
    private static ConversationMemberRepository conversationMemberRepository;
    private static ConversationController conversationController;
    private static UserController userController;

    @Autowired
    public ConversationTestUtils(ConversationRepository conversationRepository,
                                 ConversationMemberRepository conversationMemberRepository,
                                 ConversationController conversationController,
                                 UserController userController) {
        ConversationTestUtils.conversationRepository = conversationRepository;
        ConversationTestUtils.conversationMemberRepository = conversationMemberRepository;
        ConversationTestUtils.conversationController = conversationController;
        ConversationTestUtils.userController = userController;
    }

    public static Conversation createDirectConversation(User userA, User userB) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);
        conversation = conversationRepository.save(conversation);
        createConversationMember(conversation, userA);
        createConversationMember(conversation, userB);
        return conversation;
    }

    public static GetConversationMessagesResponse getChannelMessages(Long channelId, User user) {
        return conversationController.getConversationMessagesForChannel(channelId, principalOf(user), 50, null);
    }

    public static GetConversationMessagesResponse getGroupMessages(Long conversationId, User user) {
        return conversationController.getConversationMessages(conversationId, principalOf(user), 50, null);
    }

    public static DirectConversationDTO sendInvite(User sender, String recipientEmail) {
        return conversationController.sendConversationInvite(
                new SendConversationInviteRequest(recipientEmail, false), principalOf(sender));
    }

    public static DirectConversationDTO sendEncryptedInvite(User sender, String recipientEmail) {
        return conversationController.sendConversationInvite(
                new SendConversationInviteRequest(recipientEmail, true), principalOf(sender));
    }

    public static ConversationDTO createGroupConversation(User creator, List<String> memberEmails,
                                                          String name, boolean encrypted) {
        return conversationController.createGroupConversation(
                new CreateGroupConversationRequest(memberEmails, name, encrypted), principalOf(creator));
    }

    public static List<ConversationInvitePayload> getPendingInvites(User user) {
        return conversationController.getPendingInvites(principalOf(user));
    }

    public static List<SentConversationInvitePayload> getSentInvites(User user) {
        return conversationController.getSentInvites(principalOf(user));
    }

    public static List<RecentChatUsersDTO> getRecentChatUsers(User user) {
        return conversationController.getRecentChatUsers(principalOf(user));
    }

    public static UnreadConversationsDTO getUnreadConversations(User user) {
        return conversationController.getUnreadConversations(principalOf(user));
    }

    public static Conversation getConversationById(Long conversationId) {
        return conversationRepository.findById(conversationId).orElseThrow();
    }

    public static void acceptInvite(Long conversationId, User user) {
        conversationController.acceptConversationInvite(conversationId, principalOf(user));
    }

    public static void markConversationAsRead(Long conversationId, User user) {
        conversationController.markConversationAsRead(conversationId, principalOf(user));
    }

    public static void declineInvite(Long conversationId, User user) {
        conversationController.declineConversationInvite(conversationId, principalOf(user));
    }

    public static List<ConversationDTO> getUserConversations(User user) {
        return conversationController.getUserConversations(principalOf(user));
    }

    public static List<User> getConversationMembers(Long conversationId) {
        return conversationMemberRepository.findUsersByConversationId(conversationId);
    }

    public static Map<Long, String> getMemberPublicKeys(Long conversationId, User user) {
        return conversationController.getMemberPublicKeys(conversationId, principalOf(user));
    }

    public static ResponseEntity<Void> addMemberToConversation(Long conversationId, Long userId, User adder) {
        return conversationController.addMemberToConversation(conversationId, new AddMemberRequest(userId), principalOf(adder));
    }

    private static void createConversationMember(Conversation conversation, User user) {
        ConversationMember member = new ConversationMember();
        member.setConversation(conversation);
        member.setUser(user);
        member.setJoinedAt(Instant.now());
        conversationMemberRepository.save(member);
    }
}
