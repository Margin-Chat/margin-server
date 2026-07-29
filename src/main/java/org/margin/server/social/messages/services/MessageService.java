package org.margin.server.social.messages.services;

import org.springframework.modulith.NamedInterface;

import org.margin.server.users.api.UserLookup;
import org.margin.server.presence.PresenceService;
import org.margin.server.social.api.MessageAttachmentDTO;
import org.margin.server.social.api.MessageAttachmentLookup;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.api.ConversationType;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.conversation.services.ConversationValidationService;
import org.margin.server.social.messages.events.*;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.MessageReaction;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.messages.models.dtos.MessageReactionDTO;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.repositories.MessageReactionRepository;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@NamedInterface("api")
@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final MessageAttachmentLookup messageAttachmentLookup;
    private final UserLookup userLookup;
    private final ConversationService conversationService;
    private final PresenceService presenceService;
    private final ApplicationEventPublisher eventPublisher;
    private final MessageActions messageActions;
    private final MessageValidationService messageValidationService;
    private final ConversationValidationService conversationValidationService;

    public MessageService(MessageRepository messageRepository,
                          MessageReactionRepository messageReactionRepository,
                          MessageAttachmentLookup messageAttachmentLookup,
                          UserLookup userLookup,
                          ConversationService conversationService,
                          PresenceService presenceService,
                          ApplicationEventPublisher eventPublisher,
                          MessageActions messageActions,
                          MessageValidationService messageValidationService, ConversationValidationService conversationValidationService) {
        this.messageRepository = messageRepository;
        this.messageReactionRepository = messageReactionRepository;
        this.messageAttachmentLookup = messageAttachmentLookup;
        this.userLookup = userLookup;
        this.conversationService = conversationService;
        this.presenceService = presenceService;
        this.eventPublisher = eventPublisher;
        this.messageActions = messageActions;
        this.messageValidationService = messageValidationService;
        this.conversationValidationService = conversationValidationService;
    }

    public MessageResult createMessageForUsers(Long fromUserId, Conversation conversation, String content, List<Long> attachmentIds) {
        Message message = messageActions.createMessage(fromUserId, conversation, content, attachmentIds);
        List<Long> recipientIds = conversationService.getConversationMembers(conversation.getId());
        Conversation channelScope = channelScopeOf(conversation);
        return new MessageResult(
                MessageDTO.from(message)
                        .withAuthor(userLookup.dtoOf(message.getFromUserId()))
                        .withMarginId(getMarginId(conversation))
                        .withChannelName(channelScope.getChannel() == null ? null : channelScope.getChannel().getName())
                        .withAttachments(attachmentsFor(message.getId()))
                        .build(),
                recipientIds);
    }

    public void editMessage(Long editorId, Long recipientId, Long messageId, String content) {
        Conversation conversation = conversationService.getById(recipientId);
        Message message = getById(messageId);
        if (!message.getFromUserId().equals(editorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot edit another user's message");
        }
        message = messageActions.editMessage(message, content);
        List<Long> recipientIds = conversationService.getConversationMembers(conversation.getId());
        MessageResult result = new MessageResult(
                MessageDTO.from(message)
                        .withAuthor(userLookup.dtoOf(message.getFromUserId()))
                        .withMarginId(getMarginId(conversation))
                        .withAttachments(attachmentsFor(message.getId()))
                        .build(),
                recipientIds);

        eventPublisher.publishEvent(new MessageEditedEvent(result.message(), result.recipientIds(), conversation.getType()));
    }

    public void deleteMessage(Long userId, Long messageId, Long recipientId) {
        Conversation conversation = conversationService.getById(recipientId);
        conversationValidationService.validateUserIsInConversation(userId, conversation.getId());

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        MessageDTO messageDTO = MessageDTO.from(message)
                .withAuthor(userLookup.dtoOf(message.getFromUserId()))
                .withMarginId(getMarginId(conversation))
                .build();

        messageActions.deleteMessage(message);
        List<Long> recipientIds = conversationService.getConversationMembers(conversation.getId());
        MessageResult messageResult = new MessageResult(messageDTO, recipientIds);
        eventPublisher.publishEvent(new MessageDeletedEvent(messageResult));
    }

    public void sendMessage(Long fromUserId, String content, Long conversationId, List<Long> attachmentIds) {
        Conversation conversation = conversationService.getById(conversationId);
        messageValidationService.validateConversationIsNotPending(fromUserId, conversation);
        messageValidationService.validateNotThreadChannelConversation(conversation);
        MessageResult result = createMessageForUsers(fromUserId, conversation, content, attachmentIds);
        eventPublisher.publishEvent(new MessageSentEvent(result.message(), result.recipientIds()));
    }

    @Transactional(readOnly = true)
    public List<MessageDTO> getConversationMessages(Conversation conversation, int limit, Long before) {
        List<Message> messages = before != null
                ? messageRepository.findMessagesBefore(conversation.getId(), before, PageRequest.of(0, limit))
                : messageRepository.findRecentMessages(conversation.getId(), PageRequest.of(0, limit));

        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        Map<Long, List<MessageReactionDTO>> reactionsByMessageId = loadReactionsForMessages(messageIds, conversation.getId());
        Map<Long, List<MessageAttachmentDTO>> attachmentsByMessageId = loadAttachmentsForMessages(messageIds);

        return messages.stream()
                .map(msg ->
                        MessageDTO.from(msg)
                                .withAuthor(userLookup.dtoOf(msg.getFromUserId()))
                                .withMarginId(getMarginId(conversation))
                                .withReactions(reactionsByMessageId.getOrDefault(msg.getId(), List.of()))
                                .withAttachments(attachmentsByMessageId.getOrDefault(msg.getId(), List.of()))
                                .build())
                .collect(Collectors.collectingAndThen(Collectors.toList(), l -> {
                    java.util.Collections.reverse(l);
                    return l;
                }));
    }

    @Transactional
    public MessageReactionDTO addReaction(Long userId, Long recipientId, Long messageId, String emoji) {
        Conversation conversation = conversationService.getById(recipientId);
        conversationValidationService.validateUserIsInConversation(userId, conversation.getId());

        messageValidationService.validateDuplicateEmojiForMessage(userId, messageId, emoji);
        MessageReaction reaction = messageActions.createMessageReaction(userId, getById(messageId), emoji);
        MessageReactionDTO reactionDTO = MessageReactionDTO.from(reaction, conversation.getId(), userLookup.dtoOf(reaction.getUserId()).displayName());

        List<Long> recipientIds = conversationService.getConversationMembers(conversation.getId());
        eventPublisher.publishEvent(new ReactionAddedEvent(reactionDTO, recipientIds, conversation.getType()));

        return reactionDTO;
    }

    @Transactional
    public MessageReactionDTO removeReaction(Long userId,
                                             Long messageId,
                                             String emoji,
                                             Long recipientId) {
        Conversation conversation = conversationService.getById(recipientId);
        conversationValidationService.validateUserIsInConversation(userId, conversation.getId());

        MessageReaction reaction = messageReactionRepository
                .findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        MessageReactionDTO dto = MessageReactionDTO.from(reaction, conversation.getId(), userLookup.dtoOf(reaction.getUserId()).displayName());
        messageReactionRepository.delete(reaction);

        List<Long> recipientIds = conversationService.getConversationMembers(conversation.getId());
        eventPublisher.publishEvent(new ReactionRemovedEvent(dto, recipientIds, conversation.getType()));

        return dto;
    }

    private Map<Long, List<MessageReactionDTO>> loadReactionsForMessages(List<Long> messageIds, Long conversationId) {
        if (messageIds.isEmpty()) return Map.of();
        return messageReactionRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(
                        r -> r.getMessage().getId(),
                        Collectors.mapping(r -> MessageReactionDTO.from(r, conversationId, userLookup.dtoOf(r.getUserId()).displayName()), Collectors.toList())
                ));
    }

    private Map<Long, List<MessageAttachmentDTO>> loadAttachmentsForMessages(List<Long> messageIds) {
        if (messageIds.isEmpty()) return Map.of();
        return messageAttachmentLookup.findByMessageIds(messageIds);
    }

    private List<MessageAttachmentDTO> attachmentsFor(Long messageId) {
        return loadAttachmentsForMessages(List.of(messageId)).getOrDefault(messageId, List.of());
    }

    public Message getById(Long messageId) {
        return messageRepository.findById(messageId).orElseThrow();
    }

    public List<Message> getByIds(List<Long> messageIds) {
        return messageRepository.findAllById(messageIds);
    }

    private Long getMarginId(Conversation conversation) {
        Conversation scope = channelScopeOf(conversation);
        if (scope.getChannel() == null) return null;
        return scope.getChannel().getSpace().getMargin().getId();
    }

    private Conversation channelScopeOf(Conversation conversation) {
        if (conversation.getType() == ConversationType.THREAD && conversation.getParentConversationId() != null) {
            return conversationService.getById(conversation.getParentConversationId());
        }
        return conversation;
    }
}
