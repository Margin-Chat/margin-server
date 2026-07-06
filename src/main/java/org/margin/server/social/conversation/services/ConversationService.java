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
import org.margin.server.social.conversation.events.ConversationInviteAcceptedEvent;
import org.margin.server.social.conversation.events.ConversationInviteDeclinedEvent;
import org.margin.server.social.conversation.events.ConversationInviteEvent;
import org.margin.server.social.conversation.events.ConversationReadEvent;
import org.margin.server.social.conversation.events.TypingIndicatorEvent;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.payloads.ConversationInvitePayload;
import org.margin.server.websocket.models.payloads.SentConversationInvitePayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
    private final UserService userService;

    @Autowired
    public ConversationService(ConversationCreationService conversationCreationService,
                               ConversationRepository conversationRepository,
                               ConversationMemberRepository conversationMemberRepository,
                               UserRepository userRepository,
                               @Lazy ConversationService self,
                               ConnectionManager connectionManager,
                               ApplicationEventPublisher eventPublisher,
                               UserService userService) {
        this.conversationCreationService = conversationCreationService;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userRepository = userRepository;
        this.self = self;
        this.connectionManager = connectionManager;
        this.eventPublisher = eventPublisher;
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

    public void notifyTyping(User user, Conversation conversation, boolean isTyping) {
        List<User> recipients = getConversationMembers(conversation.getId()).stream()
                .filter(member -> !member.getId().equals(user.getId()))
                .toList();
        eventPublisher.publishEvent(new TypingIndicatorEvent(conversation.getId(), user, isTyping, recipients));
    }

    public List<User> getPendingConversationMembers(Long conversationId) {
        return conversationMemberRepository.findPendingUsersByConversationId(conversationId);
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
                        currentMember.getInviteStatus(),
                        conversation.isEncrypted()
                );
            }
            case GROUP -> {
                List<Long> memberIds = getConversationMembers(conversation.getId()).stream()
                        .map(User::getId)
                        .toList();
                List<Long> pendingMemberIds = getPendingConversationMembers(conversation.getId()).stream()
                        .map(User::getId)
                        .toList();
                yield new GroupConversationDTO(
                        conversation.getId(),
                        conversation.getCreatedAt(),
                        conversation.getName(),
                        memberIds,
                        pendingMemberIds,
                        conversation.isEncrypted()
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

    @Transactional
    public Conversation createGroupConversation(User creator, List<String> memberEmails, String name, boolean encrypted) {
        Conversation conversation = conversationCreationService.createGroupConversation(name, encrypted);

        // Creator is immediately an accepted member
        conversationCreationService.createConversationMember(conversation, creator);

        // All other members are invited (PENDING)
        for (String email : memberEmails) {
            User invitee = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "User not found: " + email));
            conversationCreationService.createConversationMemberWithStatus(
                    conversation, invitee, ConversationInviteStatus.PENDING);

            GroupConversationDTO groupDTO = new GroupConversationDTO(
                    conversation.getId(),
                    conversation.getCreatedAt(),
                    conversation.getName(),
                    List.of(),
                    List.of(),
                    encrypted
            );
            eventPublisher.publishEvent(new ConversationInviteEvent(groupDTO, creator, invitee.getId()));
        }

        return conversation;
    }

    @Transactional
    @CacheEvict(value = "conversations", key = "#conversationId")
    public void addMember(Long conversationId, Long userId, User adder) {
        Conversation conversation = self.getById(conversationId);
        User invitee = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member or has a pending invite");
        }

        conversationCreationService.createConversationMemberWithStatus(
                conversation, invitee, ConversationInviteStatus.PENDING);

        GroupConversationDTO groupDTO = new GroupConversationDTO(
                conversation.getId(),
                conversation.getCreatedAt(),
                conversation.getName(),
                List.of(),
                List.of(),
                conversation.isEncrypted()
        );
        eventPublisher.publishEvent(new ConversationInviteEvent(groupDTO, adder, invitee.getId()));
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
                    eventPublisher.publishEvent(new ConversationReadEvent(conversationId, conversationMember.getUser().getId(), now)));
        }

    }

    public UnreadConversationsDTO getUnreadConversations(Long userId) {
        var projections = conversationMemberRepository.getUnreadConversations(userId);

        List<Long> direct = projections.stream()
                .filter(p -> "DIRECT".equals(p.getType()))
                .map(UnreadConversationProjection::getConversationId)
                .toList();

        List<Long> group = projections.stream()
                .filter(p -> "GROUP".equals(p.getType()))
                .map(UnreadConversationProjection::getConversationId)
                .toList();

        List<UnreadConversationsDTO.ChannelUnread> channelUnreads = projections.stream()
                .filter(p -> "CHANNEL".equals(p.getType()))
                .map(p ->
                        new UnreadConversationsDTO.ChannelUnread(p.getConversationId(), p.getMarginId()))
                .toList();

        return new UnreadConversationsDTO(direct, group, channelUnreads);
    }

    @Transactional(readOnly = true)
    public List<RecentChatUsersDTO> getRecentChatUsers(Long userId) {
        return conversationMemberRepository.findRecentChatUsers(userId)
                .stream()
                .collect(Collectors.toMap(
                        p -> p.conversation().getId(),
                        p -> p,
                        (a, b) -> {
                            if (a.lastMessageTime() == null) return b;
                            if (b.lastMessageTime() == null) return a;
                            return a.lastMessageTime().isAfter(b.lastMessageTime()) ? a : b;
                        }
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
    public Conversation createNewDirectConversation(User user, User recipientUser, boolean encrypted) {
        Conversation directConversation = conversationCreationService.createDirectConversation(encrypted);
        conversationCreationService.createConversationMember(directConversation, user);
        conversationCreationService.createConversationMember(directConversation, recipientUser);
        return directConversation;
    }

    @Transactional
    public DirectConversationDTO sendConversationInvite(User sender, User recipient, boolean encrypted) {
        Conversation existing = findDirectConversationBetweenUsers(sender.getId(), recipient.getId());
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conversation already exists");
        }

        Conversation conversation = conversationCreationService.createDirectConversation(encrypted);
        conversationCreationService.createConversationMember(conversation, sender);
        conversationCreationService.createConversationMemberWithStatus(conversation, recipient,
                ConversationInviteStatus.PENDING);

        DirectConversationDTO dto = new DirectConversationDTO(
                conversation.getId(),
                conversation.getCreatedAt(),
                recipient.getId(),
                null,
                ConversationInviteStatus.ACCEPTED,
                encrypted
        );

        eventPublisher.publishEvent(new ConversationInviteEvent(
                new DirectConversationDTO(
                        conversation.getId(),
                        conversation.getCreatedAt(),
                        sender.getId(),
                        null,
                        ConversationInviteStatus.PENDING,
                        encrypted
                ),
                sender,
                recipient.getId()
        ));

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

        if (conversation.getType() == ConversationType.DIRECT) {
            ConversationMember otherMember = conversation.getMembers().stream()
                    .filter(m -> !m.getUser().getId().equals(user.getId()))
                    .findFirst()
                    .orElseThrow(UserNotFoundException::new);

            eventPublisher.publishEvent(new ConversationInviteAcceptedEvent(
                    this.getConversationDTO(conversation, user.getId()),
                    userService.toDTO(member.getUser()),
                    otherMember.getId().getUserId()));
        } else {
            // Notify all other ACCEPTED members that someone joined so their member list stays current
            ConversationDTO updatedDTO = getConversationDTO(conversation, user.getId());
            for (User m : getConversationMembers(conversationId)) {
                if (!m.getId().equals(user.getId())) {
                    eventPublisher.publishEvent(new ConversationInviteAcceptedEvent(
                            updatedDTO, userService.toDTO(user), m.getId()));
                }
            }
        }
    }

    @Transactional
    public void declineConversationInvite(Long conversationId, User user) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found"));

        if (member.getInviteStatus() != ConversationInviteStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending invite for this conversation");
        }

        Conversation conversation = getById(conversationId);
        if (conversation.getType() == ConversationType.GROUP) {
            conversationMemberRepository.delete(member);
        } else {
            member.setInviteStatus(ConversationInviteStatus.DECLINED);
            conversationMemberRepository.save(member);

            ConversationMember sender = conversation.getMembers().stream()
                    .filter(m -> !m.getUser().getId().equals(user.getId()))
                    .findFirst()
                    .orElseThrow(UserNotFoundException::new);

            eventPublisher.publishEvent(new ConversationInviteDeclinedEvent(
                    conversationId, userService.toDTO(user), sender.getId().getUserId()));
        }
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

                    ConversationDTO conversationDTO;
                    if (conv.getType() == ConversationType.DIRECT) {
                        conversationDTO = new DirectConversationDTO(
                                conv.getId(),
                                conv.getCreatedAt(),
                                sender.getId(),
                                null,
                                ConversationInviteStatus.PENDING,
                                conv.isEncrypted()
                        );
                    } else {
                        List<Long> memberIds = getConversationMembers(conv.getId()).stream()
                                .map(User::getId)
                                .toList();
                        List<Long> pendingMemberIds = getPendingConversationMembers(conv.getId()).stream()
                                .map(User::getId)
                                .toList();
                        conversationDTO = new GroupConversationDTO(
                                conv.getId(),
                                conv.getCreatedAt(),
                                conv.getName(),
                                memberIds,
                                pendingMemberIds,
                                conv.isEncrypted()
                        );
                    }
                    return new ConversationInvitePayload(conversationDTO,
                            new UserDTO(sender, connectionManager.isUserOnline(sender.getId())));
                })
                .toList();
    }

    public List<SentConversationInvitePayload> getSentInvites(Long userId) {
        return conversationMemberRepository
                .findSentDirectInvitesBySenderId(userId)
                .stream()
                .map(member -> {
                    Conversation conv = member.getConversation();
                    User recipient = member.getUser();

                    ConversationDTO conversationDTO = new DirectConversationDTO(
                            conv.getId(),
                            conv.getCreatedAt(),
                            recipient.getId(),
                            null,
                            ConversationInviteStatus.ACCEPTED,
                            conv.isEncrypted()
                    );

                    return new SentConversationInvitePayload(conversationDTO,
                            new UserDTO(recipient, connectionManager.isUserOnline(recipient.getId())));
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

    public java.util.Map<Long, String> getMemberPublicKeys(Long conversationId, Long requestingUserId) {
        if (!conversationMemberRepository.isUserMemberOfConversation(conversationId, requestingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this conversation");
        }
        List<User> members = getConversationMembers(conversationId);
        java.util.Map<Long, String> keys = new java.util.LinkedHashMap<>();
        for (User member : members) {
            if (member.getEncryption() != null && member.getEncryption().getPublicKey() != null) {
                keys.put(member.getId(), member.getEncryption().getPublicKey());
            }
        }
        return keys;
    }
}
