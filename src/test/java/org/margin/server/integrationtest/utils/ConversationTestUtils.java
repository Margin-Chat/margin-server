package org.margin.server.integrationtest.utils;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationMember;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ConversationTestUtils {

    private static ConversationRepository conversationRepository;
    private static ConversationMemberRepository conversationMemberRepository;

    @Autowired
    public ConversationTestUtils(ConversationRepository conversationRepository,
                                 ConversationMemberRepository conversationMemberRepository) {
        ConversationTestUtils.conversationRepository = conversationRepository;
        ConversationTestUtils.conversationMemberRepository = conversationMemberRepository;
    }

    public static Conversation createDirectConversation(User userA, User userB) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);
        conversation = conversationRepository.save(conversation);
        createConversationMember(conversation, userA);
        createConversationMember(conversation, userB);
        return conversation;
    }

    private static void createConversationMember(Conversation conversation, User user) {
        ConversationMember member = new ConversationMember();
        member.setConversation(conversation);
        member.setUser(user);
        member.setJoinedAt(Instant.now());
        conversationMemberRepository.save(member);
    }
}