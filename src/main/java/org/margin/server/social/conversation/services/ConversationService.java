package org.margin.server.social.conversation.services;

import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationMember;
import org.margin.server.social.conversation.models.ConversationMemberId;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.messages.models.dtos.*;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.RecentChatUsersDTO;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserRepository userRepository;
    private final ConversationService self;
    private final ConnectionManager connectionManager;

    @Autowired
    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMemberRepository conversationMemberRepository,
                               UserRepository userRepository,
                               @Lazy ConversationService self, ConnectionManager connectionManager) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userRepository = userRepository;
        this.self = self;
        this.connectionManager = connectionManager;
    }

    @Cacheable(value = "conversations", key = "#id")
    public Conversation getById(Long id) {
        return conversationRepository.findByIdWithAssociations(id)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
    }

    public List<User> getConversationMembers(Long conversationId) {
        return conversationMemberRepository.findUsersByConversationId(conversationId);
    }

    public List<Conversation> getUserConversations(Long userId) {
        return conversationRepository.findByUserId(userId);
    }

    public List<ConversationDTO> getUserConversationsDTO(Long userId) {
        return getUserConversations(userId).stream()
                .map(conv -> getConversationDTO(conv, userId))
                .toList();
    }

    public ConversationDTO getConversationDTO(Conversation conversation, Long currentUserId) {
        return switch (conversation.getType()) {
            case DIRECT -> {
                Long otherUserId = getConversationMembers(conversation.getId()).stream()
                        .filter(u -> !u.getId().equals(currentUserId))
                        .findFirst()
                        .map(User::getId)
                        .orElse(null);
                yield new DirectConversationDTO(
                        conversation.getId(),
                        conversation.getCreatedAt(),
                        otherUserId
                );
            }
            case GROUP -> {
                List<Long> memberIds = getConversationMembers(conversation.getId()).stream()
                        .map(User::getId)
                        .toList();
                yield new GroupConversationDTO(
                        conversation.getId(),
                        conversation.getCreatedAt(),
                        conversation.getName(),
                        memberIds
                );
            }
            case CHANNEL -> new ChannelConversationDTO(
                    conversation.getId(),
                    conversation.getCreatedAt(),
                    conversation.getChannel() != null ? conversation.getChannel().getId() : null,
                    conversation.getName()
            );
        };
    }

    public boolean isUserMember(Long conversationId, Long userId) {
        return conversationMemberRepository.isUserMemberOfConversation(conversationId, userId);
    }

    public Conversation findDirectConversationBetweenUsers(Long userId1, Long userId2) {
        return conversationRepository.findDirectConversationBetweenUsers(userId1, userId2)
                .orElse(null);
    }

    @Transactional
    public Conversation findOrCreateDirectConversation(User user1, User user2) {
        return conversationRepository.findDirectConversationBetweenUsers(user1.getId(), user2.getId())
                .orElseGet(() -> createDirectConversation(user1, user2));
    }

    @Transactional
    public Conversation createGroupConversation(User creator, List<Long> memberUserIds, String name) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.GROUP);
        conversation.setName(name);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation = conversationRepository.save(conversation);

        addMemberInternal(conversation, creator);

        for (Long userId : memberUserIds) {
            if (!userId.equals(creator.getId())) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userId));
                addMemberInternal(conversation, user);
            }
        }

        return conversation;
    }

    @Transactional
    @CacheEvict(value = "conversations", key = "#conversationId")
    public void addMember(Long conversationId, Long userId) {
        Conversation conversation = self.getById(conversationId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        addMemberInternal(conversation, user);
    }

    @Transactional
    @CacheEvict(value = "conversations", key = "#conversationId")
    public void removeMember(Long conversationId, Long userId) {
        ConversationMemberId id = new ConversationMemberId(conversationId, userId);
        conversationMemberRepository.deleteById(id);
    }

    @Transactional
    public void updateLastRead(Long conversationId, Long userId) {
        ConversationMemberId id = new ConversationMemberId(conversationId, userId);
        conversationMemberRepository.findById(id).ifPresent(member -> {
            member.setLastReadAt(LocalDateTime.now());
            conversationMemberRepository.save(member);
        });
    }

    public List<UnreadCountDTO> getUnreadMessagesCounts(Long userId) {
        return conversationMemberRepository.getUnreadMessagesCounts(userId);
    }

    public List<RecentChatUsersDTO> getRecentChatUsers(Long userId) {
        return conversationMemberRepository.findRecentChatUsers(userId)
                .stream()
                .map(p -> new RecentChatUsersDTO(
                        new UserDTO(p.user(), connectionManager.isUserOnline(p.user().getId())),
                        p.lastMessage(),
                        p.lastMessageTime(),
                        p.lastMessageIncoming()
                ))
                .toList();
    }

    public Conversation getByChannelId(Long channelId) {
        return conversationRepository.findByChannelId(channelId)
                .orElseThrow(() -> new RuntimeException("No conversation found for channel: " + channelId));
    }

    private Conversation createDirectConversation(User user1, User user2) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation = conversationRepository.save(conversation);

        addMemberInternal(conversation, user1);
        addMemberInternal(conversation, user2);

        return conversation;
    }

    private void addMemberInternal(Conversation conversation, User user) {
        ConversationMember member = new ConversationMember();
        member.setId(new ConversationMemberId(conversation.getId(), user.getId()));
        member.setConversation(conversation);
        member.setUser(user);
        member.setJoinedAt(LocalDateTime.now());
        conversationMemberRepository.save(member);
    }
}