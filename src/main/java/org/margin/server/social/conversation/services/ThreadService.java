package org.margin.server.social.conversation.services;

import lombok.extern.log4j.Log4j2;
import org.margin.server.presence.PresenceService;
import org.margin.server.social.channel.ChannelLookup;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.models.ChannelType;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationMember;
import org.margin.server.social.conversation.models.ConversationMemberId;
import org.margin.server.social.api.ConversationType;
import org.margin.server.social.conversation.models.dtos.ThreadConversationDTO;
import org.margin.server.social.conversation.models.dtos.ThreadSummaryDTO;
import org.margin.server.social.conversation.models.projections.ThreadSummaryProjection;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
import org.margin.server.social.conversation.repositories.ConversationRepository;
import org.margin.server.social.messages.events.MessageSentEvent;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.messages.services.MessageService;
import org.margin.server.users.api.UserLookup;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Log4j2
public class ThreadService {
    private static final int EXCERPT_MAX_LENGTH = 200;
    private static final int TITLE_MAX_LENGTH = 100;

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationCreationService conversationCreationService;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final ChannelLookup channelLookup;
    private final PresenceService presenceService;
    private final UserLookup userLookup;

    public ThreadService(ConversationRepository conversationRepository,
                         ConversationMemberRepository conversationMemberRepository,
                         ConversationCreationService conversationCreationService,
                         ConversationService conversationService,
                         MessageService messageService,
                         ChannelLookup channelLookup,
                         PresenceService presenceService,
                         UserLookup userLookup) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.conversationCreationService = conversationCreationService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.channelLookup = channelLookup;
        this.presenceService = presenceService;
        this.userLookup = userLookup;
    }

    public ThreadConversationDTO createPost(User user, Long channelId, String title, String body) {
        Conversation channelConversation = requireThreadChannelConversation(channelId);
        requireParentMember(user, channelConversation.getId());

        String trimmedTitle = title != null ? title.trim() : "";
        String trimmedBody = body != null ? body.trim() : "";
        if (trimmedTitle.isEmpty() || trimmedTitle.length() > TITLE_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Post title must be 1-" + TITLE_MAX_LENGTH + " characters");
        }
        if (trimmedBody.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Post body must not be empty");
        }

        Conversation thread = conversationCreationService.createThreadConversation(channelConversation, trimmedTitle);
        follow(thread, user);
        messageService.sendMessage(user, trimmedBody, thread.getId(), List.of());

        return conversationService.toThreadDTO(thread, user.getId());
    }

    @Transactional(readOnly = true)
    public List<ThreadSummaryDTO> getPostsForChannel(User user, Long channelId) {
        Conversation channelConversation = requireThreadChannelConversation(channelId);
        requireParentMember(user, channelConversation.getId());

        List<Conversation> posts = conversationRepository.findByParentConversationId(channelConversation.getId());
        return buildSummaries(posts, user);
    }

    public ThreadConversationDTO getThread(User user, Long threadConversationId) {
        Conversation thread = requireThread(threadConversationId);
        requireParentMember(user, thread.getParentConversationId());
        return conversationService.toThreadDTO(thread, user.getId());
    }

    public void followThread(User user, Long threadConversationId) {
        Conversation thread = requireThread(threadConversationId);
        requireParentMember(user, thread.getParentConversationId());
        follow(thread, user);
    }

    public void unfollowThread(User user, Long threadConversationId) {
        Conversation thread = requireThread(threadConversationId);
        conversationMemberRepository.deleteById(new ConversationMemberId(thread.getId(), user.getId()));
    }

    @Transactional(readOnly = true)
    public List<ThreadSummaryDTO> getFollowedThreads(User user) {
        List<Conversation> threads = conversationMemberRepository
                .findThreadMembershipsByUserId(user.getId()).stream()
                .map(ConversationMember::getConversation)
                .toList();
        return buildSummaries(threads, user);
    }

    @EventListener
    public void onMessageSent(MessageSentEvent event) {
        MessageDTO message = event.getMessage();
        if (message.conversationType() != ConversationType.THREAD) {
            return;
        }
        Long senderId = message.user().id();
        boolean alreadyFollowing = conversationMemberRepository
                .findByConversationIdAndUserId(message.conversationId(), senderId).isPresent();
        if (alreadyFollowing) {
            return;
        }
        if (event.getRecipientIds().contains(senderId)) {
            follow(conversationService.getById(message.conversationId()), userLookup.findById(senderId).orElseThrow());
        }
    }

    private List<ThreadSummaryDTO> buildSummaries(List<Conversation> threads, User user) {
        if (threads.isEmpty()) {
            return List.of();
        }
        List<Long> threadIds = threads.stream().map(Conversation::getId).toList();
        Map<Long, ThreadSummaryProjection> summariesByThreadId = conversationRepository
                .findThreadSummariesForThreads(threadIds, user.getId()).stream()
                .collect(Collectors.toMap(ThreadSummaryProjection::getThreadConversationId, Function.identity()));

        List<Long> firstMessageIds = summariesByThreadId.values().stream()
                .map(ThreadSummaryProjection::getFirstMessageId)
                .filter(id -> id != null)
                .toList();
        Map<Long, Message> firstMessagesById = messageService.getByIds(firstMessageIds).stream()
                .collect(Collectors.toMap(Message::getId, Function.identity()));

        Map<Long, ConversationMember> membershipsByThreadId = conversationMemberRepository
                .findThreadMembershipsByUserId(user.getId()).stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), Function.identity()));

        return threads.stream()
                .map(thread -> {
                    ThreadSummaryProjection summary = summariesByThreadId.get(thread.getId());
                    Message firstMessage = summary != null && summary.getFirstMessageId() != null
                            ? firstMessagesById.get(summary.getFirstMessageId())
                            : null;
                    return toSummary(thread, user, summary, firstMessage, membershipsByThreadId.get(thread.getId()));
                })
                .sorted(Comparator.comparing(ThreadSummaryDTO::lastReplyAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private ThreadSummaryDTO toSummary(Conversation thread, User user, ThreadSummaryProjection summary,
                                       Message firstMessage, ConversationMember membership) {
        long messageCount = summary != null && summary.getMessageCount() != null ? summary.getMessageCount() : 0;
        long replyCount = Math.max(0, messageCount - 1);
        Instant lastReplyAt = summary != null ? summary.getLastReplyAt() : null;
        Instant lastOtherReplyAt = summary != null ? summary.getLastOtherReplyAt() : null;

        boolean unread = false;
        if (membership != null && lastOtherReplyAt != null) {
            Instant readHorizon = membership.getLastReadAt() != null
                    ? membership.getLastReadAt()
                    : membership.getJoinedAt();
            unread = lastOtherReplyAt.isAfter(readHorizon);
        }

        UserDTO author = firstMessage != null
                ? userLookup.dtoOf(firstMessage.getFromUserId())
                : null;

        return new ThreadSummaryDTO(
                conversationService.toThreadDTO(thread, user.getId()),
                author,
                firstMessage != null ? excerptOf(firstMessage) : null,
                replyCount,
                lastReplyAt,
                unread
        );
    }

    private String excerptOf(Message message) {
        String content = message.getMessage();
        if (content == null || content.length() <= EXCERPT_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, EXCERPT_MAX_LENGTH);
    }

    private void follow(Conversation thread, User user) {
        if (conversationMemberRepository.findByConversationIdAndUserId(thread.getId(), user.getId()).isEmpty()) {
            conversationCreationService.createConversationMember(thread, user.getId());
        }
    }

    private Conversation requireThreadChannelConversation(Long channelId) {
        Channel channel;
        try {
            channel = channelLookup.getById(channelId);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found");
        }
        if (channel.getChannelType() != ChannelType.Thread) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Posts can only be created in thread channels");
        }
        return conversationService.getByChannelId(channelId);
    }

    private Conversation requireThread(Long conversationId) {
        Conversation conversation;
        try {
            conversation = conversationService.getById(conversationId);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread not found");
        }
        if (conversation.getType() != ConversationType.THREAD) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thread not found");
        }
        return conversation;
    }

    private void requireParentMember(User user, Long parentConversationId) {
        if (!conversationService.isUserMember(parentConversationId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this conversation");
        }
    }
}
