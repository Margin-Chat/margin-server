package org.margin.server.social.conversation.services;

import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.conversation.models.*;
import org.margin.server.social.conversation.models.dtos.*;
import org.margin.server.social.conversation.models.projections.UnreadConversationProjection;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.users.exceptions.UserNotFoundException;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.RecentChatUsersDTO;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.payloads.ConversationInvitePayload;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ConversationService {
    private final ConversationCreationService conversationCreationService;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserRepository userRepository;
    private final ConversationService self;
    private final ConnectionManager connectionManager;
    private final WebSocketDeliveryService webSocketDeliveryService;
    private final UserService userService;

    @Autowired
    public ConversationService(ConversationCreationService conversationCreationService,
                               ConversationRepository conversationRepository,
                               ConversationMemberRepository conversationMemberRepository,
                               UserRepository userRepository,
                               @Lazy ConversationService self,
                               ConnectionManager connectionManager,
                               WebSocketDeliveryService webSocketDeliveryService,
                               UserService userService) {
        this.conversationCreationService = conversationCreationService;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userRepository = userRepository;
        this.self = self;
        this.connectionManager = connectionManager;
        this.webSocketDeliveryService = webSocketDeliveryService;
        this.userService = userService;
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
                ConversationMember otherMember = conversation.getMembers().stream()
                        .filter(member -> !member.getUser().getId().equals(currentUserId))
                        .findFirst()
                        .orElseThrow(UserNotFoundException::new);
                ConversationMember currentMember = conversation.getMembers().stream()
                        .filter(member -> member.getUser().getId().equals(currentUserId))
                        .findFirst()
                        .orElseThrow(UserNotFoundException::new);
                yield new DirectConversationDTO(
                        conversation.getId(),
                        conversation.getCreatedAt(),
                        otherMember.getUser().getId(),
                        otherMember.getLastReadAt(),
                        currentMember.getInviteStatus()
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
                .stream().findFirst().orElse(null);
    }

    public Conversation createGroupConversation(List<Long> memberUserIds, String name) {
        Conversation conversation = conversationCreationService.createGroupConversation(name);

        for (Long userId : memberUserIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            conversationCreationService.createConversationMember(conversation, user);
        }

        return conversation;
    }

    @Transactional
    @CacheEvict(value = "conversations", key = "#conversationId")
    public void addMember(Long conversationId, Long userId) {
        Conversation conversation = self.getById(conversationId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        conversationCreationService.createConversationMember(conversation, user);
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
        Instant now = Instant.now();
        conversationMemberRepository.findById(id).ifPresent(member -> {
            member.setLastReadAt(now);
            conversationMemberRepository.save(member);
        });

        Conversation conversation = getById(conversationId);
        if (conversation.getType().equals(ConversationType.DIRECT)) {
            Optional<ConversationMember> otherUser = conversation.getMembers().stream()
                    .filter(member -> !member.getUser().getId().equals(userId))
                    .findFirst();
            otherUser.ifPresent(conversationMember ->
                    webSocketDeliveryService.notifyConversationRead(conversationId, conversationMember.getUser().getId(), now));
        }

    }

    public UnreadConversationsDTO getUnreadConversations(Long userId) {
        var projections = conversationMemberRepository.getUnreadConversations(userId);

        List<Long> direct = projections.stream()
                .filter(p -> "DIRECT".equals(p.getType()))
                .map(UnreadConversationProjection::getConversationId)
                .toList();

        List<UnreadConversationsDTO.ChannelUnread> channelUnreads = projections.stream()
                .filter(p -> "CHANNEL".equals(p.getType()))
                .map(p ->
                        new UnreadConversationsDTO.ChannelUnread(p.getConversationId(), p.getMarginId()))
                .toList();

        return new UnreadConversationsDTO(direct, channelUnreads);
    }

    public List<RecentChatUsersDTO> getRecentChatUsers(Long userId) {
        return conversationMemberRepository.findRecentChatUsers(userId)
                .stream()
                .collect(Collectors.toMap(
                        p -> p.user().getId(),
                        p -> p,
                        (a, b) -> a.lastMessageTime().isAfter(b.lastMessageTime()) ? a : b
                ))
                .values()
                .stream()
                .map(p -> new RecentChatUsersDTO(
                        getConversationDTO(p.conversation(), userId),
                        new UserDTO(p.user(), connectionManager.isUserOnline(p.user().getId())),
                        p.lastMessage(),
                        p.lastMessageTime(),
                        p.lastMessageIncoming()
                ))
                .toList();
    }

    public Instant getOtherUserLastReadAt(Long conversationId, Long currentUserId) {
        return getConversationMembers(conversationId).stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .findFirst()
                .map(u -> conversationMemberRepository
                        .findById(new ConversationMemberId(conversationId, u.getId()))
                        .map(ConversationMember::getLastReadAt)
                        .orElse(Instant.EPOCH))
                .orElse(Instant.EPOCH);
    }

    public Conversation getByChannelId(Long channelId) {
        return conversationRepository.findByChannelId(channelId)
                .orElseThrow(() -> new RuntimeException("No conversation found for channel: " + channelId));
    }

    @Transactional
    public Conversation createNewDirectConversation(User user, User recipientUser) {
        Conversation directConversation = conversationCreationService.createDirectConversation();
        conversationCreationService.createConversationMember(directConversation, user);
        conversationCreationService.createConversationMember(directConversation, recipientUser);
        return directConversation;
    }

    @Transactional
    public DirectConversationDTO sendConversationInvite(User sender, User recipient) {
        Conversation existing = findDirectConversationBetweenUsers(sender.getId(), recipient.getId());
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conversation already exists");
        }

        Conversation conversation = conversationCreationService.createDirectConversation();
        conversationCreationService.createConversationMember(conversation, sender);
        conversationCreationService.createConversationMemberWithStatus(conversation, recipient,
                ConversationInviteStatus.PENDING);

        DirectConversationDTO dto = new DirectConversationDTO(
                conversation.getId(),
                conversation.getCreatedAt(),
                recipient.getId(),
                null,
                ConversationInviteStatus.ACCEPTED
        );

        webSocketDeliveryService.notifyConversationInvite(
                new DirectConversationDTO(
                        conversation.getId(),
                        conversation.getCreatedAt(),
                        sender.getId(),
                        null,
                        ConversationInviteStatus.PENDING
                ),
                sender,
                recipient.getId()
        );

        return dto;
    }

    @Transactional
    public void acceptConversationInvite(Long conversationId, User user) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found"));

        if (member.getInviteStatus() != ConversationInviteStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending invite for this conversation");
        }

        member.setInviteStatus(ConversationInviteStatus.ACCEPTED);
        conversationMemberRepository.save(member);
        Conversation conversation = member.getConversation();

        ConversationMember otherMember = conversation.getMembers().stream()
                .filter(m -> !m.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow(UserNotFoundException::new);

        webSocketDeliveryService.notifyConversationInviteAccepted(
                this.getConversationDTO(conversation, user.getId()),
                userService.toDTO(member.getUser()),
                otherMember.getId().getUserId());
    }

    @Transactional
    public void declineConversationInvite(Long conversationId, User user) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found"));

        if (member.getInviteStatus() != ConversationInviteStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending invite for this conversation");
        }

        member.setInviteStatus(ConversationInviteStatus.DECLINED);
        conversationMemberRepository.save(member);
    }

    public List<ConversationInvitePayload> getPendingInvites(Long userId) {
        return conversationMemberRepository
                .findByUserIdAndInviteStatus(userId, ConversationInviteStatus.PENDING)
                .stream()
                .map(member -> {
                    Conversation conv = member.getConversation();
                    User sender = conversationMemberRepository
                            .findUsersByConversationId(conv.getId())
                            .stream()
                            .filter(u -> !u.getId().equals(userId))
                            .findFirst()
                            .orElseThrow();
                    DirectConversationDTO conversation = new DirectConversationDTO(
                            conv.getId(),
                            conv.getCreatedAt(),
                            sender.getId(),
                            null,
                            ConversationInviteStatus.PENDING
                    );
                    return new ConversationInvitePayload(conversation, new UserDTO(sender, connectionManager.isUserOnline(sender.getId())));
                })
                .toList();
    }

    public Conversation createNewConversationForUsers(ConversationType type, Channel channel, List<User> users) {
        Conversation conversation = conversationCreationService.createChannelConversation(type, channel);
        users.forEach(user -> conversationCreationService.createConversationMember(conversation, user));
        return conversation;
    }

    public void createNewConversationMember(Conversation conversation, User user) {
        conversationCreationService.createConversationMember(conversation, user);
    }
}