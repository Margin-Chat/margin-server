package org.margin.server.social.conversation.services;

import lombok.extern.log4j.Log4j2;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationInviteStatus;
import org.margin.server.social.conversation.models.ConversationMember;
import org.margin.server.social.conversation.models.ConversationMemberId;
import org.margin.server.social.api.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.users.models.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Log4j2
public class ConversationCreationService {
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;

    public ConversationCreationService(ConversationRepository conversationRepository,
                                       ConversationMemberRepository conversationMemberRepository) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
    }

    @Transactional
    public Conversation createChannelConversation(ConversationType conversationType, Channel channel) {
        Conversation conversation = new Conversation();
        conversation.setType(conversationType);
        conversation.setCreatedAt(Instant.now());
        conversation.setChannel(channel);
        conversation.setName(channel.getName());
        conversation = conversationRepository.save(conversation);
        log.info("Channel Conversation has been created: {}", conversation.getId());
        return conversation;
    }

    @Transactional
    public Conversation createDirectConversation(boolean encrypted) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);
        conversation.setEncrypted(encrypted);
        conversation.setCreatedAt(Instant.now());
        conversation = conversationRepository.save(conversation);
        log.info("Direct Conversation has been created: {}", conversation.getId());
        return conversation;
    }

    @Transactional
    public Conversation createThreadConversation(Conversation channelConversation, String title) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.THREAD);
        conversation.setCreatedAt(Instant.now());
        conversation.setName(title);
        conversation.setParentConversationId(channelConversation.getId());
        conversation = conversationRepository.save(conversation);
        log.info("Thread post has been created: {} ('{}' in channel conversation {})",
                conversation.getId(), title, channelConversation.getId());
        return conversation;
    }

    @Transactional
    public Conversation createGroupConversation(String name, boolean encrypted) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.GROUP);
        conversation.setName(name);
        conversation.setEncrypted(encrypted);
        conversation.setCreatedAt(Instant.now());
        conversation = conversationRepository.save(conversation);
        log.info("Group Conversation has been created: {}", conversation.getId());
        return conversation;
    }

    @Transactional
    public void createConversationMember(Conversation conversation, User user) {
        createConversationMemberWithStatus(conversation, user, ConversationInviteStatus.ACCEPTED);
    }

    @Transactional
    public void createConversationMemberWithStatus(Conversation conversation, User user,
                                                   ConversationInviteStatus status) {
        ConversationMember member = new ConversationMember();
        member.setId(new ConversationMemberId(conversation.getId(), user.getId()));
        member.setConversation(conversation);
        member.setUser(user);
        member.setJoinedAt(Instant.now());
        member.setInviteStatus(status);
        if (status == ConversationInviteStatus.PENDING) {
            member.setInvitedAt(Instant.now());
        }
        log.info("Creation Conversation Member: {}, status: {}, for Conversation {}",
                member.getUser().getId(), status, conversation.getId());
        conversationMemberRepository.save(member);
    }
}
