package org.margin.server.social.conversation.services;

import org.springframework.modulith.NamedInterface;

import org.margin.server.social.api.ConversationType;
import org.margin.server.presence.PresenceService;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.conversation.events.ConversationInviteAcceptedEvent;
import org.margin.server.social.conversation.events.ConversationInviteDeclinedEvent;
import org.margin.server.social.conversation.events.ConversationInviteEvent;
import org.margin.server.social.conversation.events.ConversationReadEvent;
import org.margin.server.social.conversation.events.TypingIndicatorEvent;
import org.margin.server.social.conversation.models.*;
import org.margin.server.social.conversation.models.dtos.*;
import org.margin.server.social.conversation.models.projections.ThreadSummaryProjection;
import org.margin.server.social.conversation.models.projections.UnreadConversationProjection;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.users.api.UserSummary;
import org.margin.server.users.exceptions.UserNotFoundException;
import org.margin.server.social.conversation.models.dtos.RecentChatUsersDTO;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.api.UserLookup;
import org.margin.server.social.conversation.models.dtos.ConversationInvitePayload;
import org.margin.server.social.conversation.models.dtos.SentConversationInvitePayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@NamedInterface("api")
@Service
public class ConversationService {
    private final ConversationCreationService conversationCreationService;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserLookup userLookup;
    private final ConversationService self;
    private final PresenceService presenceService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public ConversationService(ConversationCreationService conversationCreationService,
                               ConversationRepository conversationRepository,
                               ConversationMemberRepository conversationMemberRepository,
                               UserLookup userLookup,
                               @Lazy ConversationService self,
                               PresenceService presenceService,
                               ApplicationEventPublisher eventPublisher) {
        this.conversationCreationService = conversationCreationService;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userLookup = userLookup;
        this.self = self;
        this.presenceService = presenceService;
        this.eventPublisher = eventPublisher;
    }

    @Cacheable(value = "conversations", key = "#id")
    public Conversation getById(Long id) {
        return conversationRepository.findByIdWithAssociations(id)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
    }

    public List<Long> getConversationMembers(Long conversationId) {
        return conversationMemberRepository.findUserIdsByConversationId(resolveMembershipConversationId(conversationId));
    }

    private Long resolveMembershipConversationId(Long conversationId) {
        Conversation conversation = self.getById(conversationId);
        if (conversation.getType() == ConversationType.THREAD && conversation.getParentConversationId() != null) {
            return conversation.getParentConversationId();
        }
        return conversationId;
    }

    public List<Long> getThreadFollowers(Long threadConversationId) {
        return conversationMemberRepository.findUserIdsByConversationId(threadConversationId);
    }


    public void notifyTyping(Long userId, Long conversationId, boolean isTyping) {
        Conversation conversation = getById(conversationId);
        List<Long> recipientIds = getConversationMembers(conversation.getId()).stream()
                .filter(memberId -> !memberId.equals(userId))
                .toList();
        eventPublisher.publishEvent(new TypingIndicatorEvent(conversation.getId(), new UserSummary(userId, userLookup.dtoOf(userId).displayName()), isTyping,
                recipientIds));
    }

    public List<Long> getPendingConversationMembers(Long conversationId) {
        return conversationMemberRepository.findPendingUserIdsByConversationId(conversationId);
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
                        .filter(member -> !member.getUserId().equals(currentUserId))
                        .findFirst()
                        .orElseThrow(UserNotFoundException::new);
                ConversationMember currentMember = conversation.getMembers().stream()
                        .filter(member -> member.getUserId().equals(currentUserId))
                        .findFirst()
                        .orElseThrow(UserNotFoundException::new);
                yield new DirectConversationDTO(
                        conversation.getId(),
                        conversation.getCreatedAt(),
                        otherMember.getUserId(),
                        otherMember.getLastReadAt(),
                        currentMember.getInviteStatus(),
                        conversation.isEncrypted()
                );
            }
            case GROUP -> {
                List<Long> memberIds = getConversationMembers(conversation.getId());
                List<Long> pendingMemberIds = getPendingConversationMembers(conversation.getId());
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
            case THREAD -> toThreadDTO(conversation, currentUserId);
        };
    }

    public ThreadConversationDTO toThreadDTO(Conversation thread, Long currentUserId) {
        Conversation parent = self.getById(thread.getParentConversationId());
        Channel channel = parent.getChannel();
        boolean following = currentUserId != null
                && conversationMemberRepository.findByConversationIdAndUserId(thread.getId(), currentUserId).isPresent();
        return new ThreadConversationDTO(
                thread.getId(),
                thread.getCreatedAt(),
                parent.getId(),
                channel != null ? channel.getId() : null,
                channel != null ? channel.getSpace().getMargin().getId() : null,
                thread.getName(),
                following
        );
    }

    public boolean isUserMember(Long conversationId, Long userId) {
        return conversationMemberRepository.isUserMemberOfConversation(
                resolveMembershipConversationId(conversationId), userId);
    }

    public Conversation findDirectConversationBetweenUsers(Long userId1, Long userId2) {
        return conversationRepository.findDirectConversationBetweenUsers(userId1, userId2)
                .stream().findFirst().orElse(null);
    }

    @Transactional
    public Conversation createGroupConversation(Long creatorId, List<String> memberEmails, String name, boolean encrypted) {
        Conversation conversation = conversationCreationService.createGroupConversation(name, encrypted);

        // Creator is immediately an accepted member
        conversationCreationService.createConversationMember(conversation, creatorId);

        // All other members are invited (PENDING)
        for (String email : memberEmails) {
            Long inviteeId = userLookup.idByEmail(email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "User not found: " + email));
            conversationCreationService.createConversationMemberWithStatus(
                    conversation, inviteeId, ConversationInviteStatus.PENDING);

            GroupConversationDTO groupDTO = new GroupConversationDTO(
                    conversation.getId(),
                    conversation.getCreatedAt(),
                    conversation.getName(),
                    List.of(),
                    List.of(),
                    encrypted
            );
            eventPublisher.publishEvent(new ConversationInviteEvent(groupDTO, userLookup.summaryOf(creatorId), inviteeId));
        }

        return conversation;
    }

    @Transactional
    @CacheEvict(value = "conversations", key = "#conversationId")
    public void addMember(Long conversationId, Long userId, Long adderId) {
        Conversation conversation = self.getById(conversationId);

        if (conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member or has a pending invite");
        }

        conversationCreationService.createConversationMemberWithStatus(
                conversation, userId, ConversationInviteStatus.PENDING);

        GroupConversationDTO groupDTO = new GroupConversationDTO(
                conversation.getId(),
                conversation.getCreatedAt(),
                conversation.getName(),
                List.of(),
                List.of(),
                conversation.isEncrypted()
        );
        eventPublisher.publishEvent(new ConversationInviteEvent(groupDTO, userLookup.summaryOf(adderId), userId));
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
                    .filter(member -> !member.getUserId().equals(userId))
                    .findFirst();
            otherUser.ifPresent(conversationMember ->
                    eventPublisher.publishEvent(new ConversationReadEvent(conversationId, conversationMember.getUserId(), now)));
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

        List<Long> threads = projections.stream()
                .filter(p -> "THREAD".equals(p.getType()))
                .map(UnreadConversationProjection::getConversationId)
                .toList();

        return new UnreadConversationsDTO(direct, group, channelUnreads, threads);
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
                        userLookup.dtoOf(p.userId()),
                        p.lastMessage(),
                        p.lastMessageTime(),
                        p.lastMessageIncoming()
                ))
                .toList();
    }

    public Instant getOtherUserLastReadAt(Long conversationId, Long currentUserId) {
        return getConversationMembers(conversationId).stream()
                .filter(id -> !id.equals(currentUserId))
                .findFirst()
                .map(otherUserId -> conversationMemberRepository
                        .findById(new ConversationMemberId(conversationId, otherUserId))
                        .map(ConversationMember::getLastReadAt)
                        .orElse(Instant.EPOCH))
                .orElse(Instant.EPOCH);
    }

    public Conversation getByChannelId(Long channelId) {
        return conversationRepository.findByChannelId(channelId)
                .orElseThrow(() -> new RuntimeException("No conversation found for channel: " + channelId));
    }

    @Transactional
    public Conversation createNewDirectConversation(Long userId, Long recipientUserId, boolean encrypted) {
        Conversation directConversation = conversationCreationService.createDirectConversation(encrypted);
        conversationCreationService.createConversationMember(directConversation, userId);
        conversationCreationService.createConversationMember(directConversation, recipientUserId);
        return directConversation;
    }

    @Transactional
    public DirectConversationDTO sendConversationInvite(Long senderId, Long recipientId, boolean encrypted) {
        Conversation existing = findDirectConversationBetweenUsers(senderId, recipientId);
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conversation already exists");
        }

        Conversation conversation = conversationCreationService.createDirectConversation(encrypted);
        conversationCreationService.createConversationMember(conversation, senderId);
        conversationCreationService.createConversationMemberWithStatus(conversation, recipientId,
                ConversationInviteStatus.PENDING);

        DirectConversationDTO dto = new DirectConversationDTO(
                conversation.getId(),
                conversation.getCreatedAt(),
                recipientId,
                null,
                ConversationInviteStatus.ACCEPTED,
                encrypted
        );

        eventPublisher.publishEvent(new ConversationInviteEvent(
                new DirectConversationDTO(
                        conversation.getId(),
                        conversation.getCreatedAt(),
                        senderId,
                        null,
                        ConversationInviteStatus.PENDING,
                        encrypted
                ),
                userLookup.summaryOf(senderId),
                recipientId
        ));

        return dto;
    }

    @Transactional
    public void acceptConversationInvite(Long conversationId, Long userId) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite not found"));

        if (member.getInviteStatus() != ConversationInviteStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending invite for this conversation");
        }

        member.setInviteStatus(ConversationInviteStatus.ACCEPTED);
        conversationMemberRepository.save(member);
        Conversation conversation = member.getConversation();

        if (conversation.getType() == ConversationType.DIRECT) {
            ConversationMember otherMember = conversation.getMembers().stream()
                    .filter(m -> !m.getUserId().equals(userId))
                    .findFirst()
                    .orElseThrow(UserNotFoundException::new);

            eventPublisher.publishEvent(new ConversationInviteAcceptedEvent(
                    this.getConversationDTO(conversation, userId),
                    userLookup.dtoOf(member.getUserId()),
                    otherMember.getId().getUserId()));
        } else {
            // Notify all other ACCEPTED members that someone joined so their member list stays current
            ConversationDTO updatedDTO = getConversationDTO(conversation, userId);
            for (Long memberId : getConversationMembers(conversationId)) {
                if (!memberId.equals(userId)) {
                    eventPublisher.publishEvent(new ConversationInviteAcceptedEvent(
                            updatedDTO, userLookup.dtoOf(userId), memberId));
                }
            }
        }
    }

    @Transactional
    public void declineConversationInvite(Long conversationId, Long userId) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
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
                    .filter(m -> !m.getUserId().equals(userId))
                    .findFirst()
                    .orElseThrow(UserNotFoundException::new);

            eventPublisher.publishEvent(new ConversationInviteDeclinedEvent(
                    conversationId, userLookup.dtoOf(userId), sender.getId().getUserId()));
        }
    }

    public List<ConversationInvitePayload> getPendingInvites(Long userId) {
        return conversationMemberRepository
                .findByUserIdAndInviteStatus(userId, ConversationInviteStatus.PENDING)
                .stream()
                .map(member -> {
                    Conversation conv = member.getConversation();
                    Long senderId = conversationMemberRepository
                            .findUserIdsByConversationId(conv.getId())
                            .stream()
                            .filter(id -> !id.equals(userId))
                            .findFirst()
                            .orElseThrow();

                    ConversationDTO conversationDTO;
                    if (conv.getType() == ConversationType.DIRECT) {
                        conversationDTO = new DirectConversationDTO(
                                conv.getId(),
                                conv.getCreatedAt(),
                                senderId,
                                null,
                                ConversationInviteStatus.PENDING,
                                conv.isEncrypted()
                        );
                    } else {
                        List<Long> memberIds = getConversationMembers(conv.getId());
                        List<Long> pendingMemberIds = getPendingConversationMembers(conv.getId());
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
                            userLookup.dtoOf(senderId));
                })
                .toList();
    }

    public List<SentConversationInvitePayload> getSentInvites(Long userId) {
        return conversationMemberRepository
                .findSentDirectInvitesBySenderId(userId)
                .stream()
                .map(member -> {
                    Conversation conv = member.getConversation();
                    Long recipientId = member.getUserId();

                    ConversationDTO conversationDTO = new DirectConversationDTO(
                            conv.getId(),
                            conv.getCreatedAt(),
                            recipientId,
                            null,
                            ConversationInviteStatus.ACCEPTED,
                            conv.isEncrypted()
                    );

                    return new SentConversationInvitePayload(conversationDTO,
                            userLookup.dtoOf(recipientId));
                })
                .toList();
    }

    public Conversation createNewConversationForUsers(ConversationType type, Channel channel, List<Long> userIds) {
        Conversation conversation = conversationCreationService.createChannelConversation(type, channel);
        userIds.forEach(userId -> conversationCreationService.createConversationMember(conversation, userId));
        return conversation;
    }

    public void createNewConversationMember(Conversation conversation, Long userId) {
        conversationCreationService.createConversationMember(conversation, userId);
    }

    public java.util.Map<Long, String> getMemberPublicKeys(Long conversationId, Long requestingUserId) {
        if (!conversationMemberRepository.isUserMemberOfConversation(conversationId, requestingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this conversation");
        }
        return userLookup.publicKeysOf(getConversationMembers(conversationId));
    }
}
