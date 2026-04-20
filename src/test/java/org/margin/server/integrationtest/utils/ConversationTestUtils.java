package org.margin.server.integrationtest.utils;

import org.margin.server.social.conversation.controllers.ConversationController;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationMember;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.models.dtos.DirectConversationDTO;
import org.margin.server.social.conversation.models.dtos.GetConversationMessagesResponse;
import org.margin.server.social.conversation.models.dtos.SendConversationInviteRequest;
import org.margin.server.social.conversation.models.dtos.UnreadConversationsDTO;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ConversationTestUtils {

    private static ConversationRepository conversationRepository;
    private static ConversationMemberRepository conversationMemberRepository;
    private static ConversationController conversationController;

    @Autowired
    public ConversationTestUtils(ConversationRepository conversationRepository,
                                 ConversationMemberRepository conversationMemberRepository,
                                 ConversationController conversationController) {
        ConversationTestUtils.conversationRepository = conversationRepository;
        ConversationTestUtils.conversationMemberRepository = conversationMemberRepository;
        ConversationTestUtils.conversationController = conversationController;
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
        return conversationController.getConversationMessagesForChannel(channelId, user, 50, null);
    }

    public static DirectConversationDTO sendInvite(User sender, String recipientHandle) {
        return conversationController.sendConversationInvite(
                new SendConversationInviteRequest(recipientHandle), sender);
    }

    public static List<DirectConversationDTO> getPendingInvites(User user) {
        return conversationController.getPendingInvites(user);
    }

    public static UnreadConversationsDTO getUnreadConversations(User user) {
        return conversationController.getUnreadConversations(user);
    }

    public static Conversation getConversationById(Long conversationId) {
        return conversationRepository.findById(conversationId).orElseThrow();
    }

    public static void acceptInvite(Long conversationId, User user) {
        conversationController.acceptConversationInvite(conversationId, user);
    }

    public static void declineInvite(Long conversationId, User user) {
        conversationController.declineConversationInvite(conversationId, user);
    }

    private static void createConversationMember(Conversation conversation, User user) {
        ConversationMember member = new ConversationMember();
        member.setConversation(conversation);
        member.setUser(user);
        member.setJoinedAt(Instant.now());
        conversationMemberRepository.save(member);
    }
}