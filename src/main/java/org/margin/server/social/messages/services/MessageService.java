package org.margin.server.social.messages.services;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.MessageReaction;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.messages.models.dtos.MessageReactionDTO;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.repositories.MessageReactionRepository;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.margin.server.storage.StorageLookup;
import org.margin.server.storage.dtos.StoredFileDTO;
import org.margin.server.users.models.User;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final StorageLookup storageLookup;
    private final ConversationService conversationService;
    private final ConnectionManager connectionManager;
    private final WebSocketDeliveryService webSocketDeliveryService;
    private final MessageActions messageActions;
    private final MessageValidationService messageValidationService;

    public MessageService(MessageRepository messageRepository,
                          MessageReactionRepository messageReactionRepository,
                          StorageLookup storageLookup,
                          ConversationService conversationService,
                          ConnectionManager connectionManager,
                          WebSocketDeliveryService webSocketDeliveryService,
                          MessageActions messageActions,
                          MessageValidationService messageValidationService) {
        this.messageRepository = messageRepository;
        this.messageReactionRepository = messageReactionRepository;
        this.storageLookup = storageLookup;
        this.conversationService = conversationService;
        this.connectionManager = connectionManager;
        this.webSocketDeliveryService = webSocketDeliveryService;
        this.messageActions = messageActions;
        this.messageValidationService = messageValidationService;
    }

    public MessageResult createMessageForUsers(User fromUser, Conversation conversation, String content, List<Long> attachmentIds) {
        Message message = messageActions.createMessage(fromUser, conversation, content, attachmentIds);
        List<User> recipients = conversationService.getConversationMembers(conversation.getId());
        return new MessageResult(
                MessageDTO.from(message)
                        .withOnline(connectionManager.isUserOnline(message.getFromUser().getId()))
                        .withMarginId(getMarginId(conversation))
                        .withChannelName(conversation.getChannel() == null ? null : conversation.getChannel().getName())
                        .withAttachments(attachmentsFor(message.getId()))
                        .build(),
                recipients);
    }

    public MessageResult editMessage(Conversation conversation, Long messageId, String content) {
        Message message = messageActions.editMessage(getById(messageId), content);
        List<User> recipients = conversationService.getConversationMembers(conversation.getId());
        return new MessageResult(
                MessageDTO.from(message)
                        .withOnline(connectionManager.isUserOnline(message.getFromUser().getId()))
                        .withMarginId(getMarginId(conversation))
                        .withAttachments(attachmentsFor(message.getId()))
                        .build(),
                recipients);
    }

    public MessageResult deleteMessage(Long messageId, Conversation conversation) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        MessageDTO messageDTO = MessageDTO.from(message)
                .withOnline(connectionManager.isUserOnline(message.getFromUser().getId()))
                .withMarginId(getMarginId(conversation))
                .build();

        messageActions.deleteMessage(message);
        List<User> recipients = conversationService.getConversationMembers(conversation.getId());
        return new MessageResult(messageDTO, recipients);
    }

    public void sendMessage(User fromUser, String content, Conversation conversation, List<Long> attachmentIds) {
        messageValidationService.validateConversationIsNotPending(fromUser, conversation);
        MessageResult result = createMessageForUsers(fromUser, conversation, content, attachmentIds);
        webSocketDeliveryService.notifyMessage(
                result.message(),
                result.recipients(),
                result.message().conversationType()
        );
    }

    @Transactional(readOnly = true)
    public List<MessageDTO> getConversationMessages(Conversation conversation, int limit, Long before) {
        List<Message> messages = before != null
                ? messageRepository.findMessagesBefore(conversation.getId(), before, PageRequest.of(0, limit))
                : messageRepository.findRecentMessages(conversation.getId(), PageRequest.of(0, limit));

        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        Map<Long, List<MessageReactionDTO>> reactionsByMessageId = loadReactionsForMessages(messageIds, conversation.getId());
        Map<Long, List<StoredFileDTO>> attachmentsByMessageId = loadAttachmentsForMessages(messageIds);

        return messages.stream()
                .map(msg ->
                        MessageDTO.from(msg)
                                .withOnline(connectionManager.isUserOnline(msg.getFromUser().getId()))
                                .withMarginId(getMarginId(conversation))
                                .withReactions(reactionsByMessageId.getOrDefault(msg.getId(), List.of()))
                                .withAttachments(attachmentsByMessageId.getOrDefault(msg.getId(), List.of()))
                                .build())
                .collect(Collectors.collectingAndThen(Collectors.toList(), l -> {
                    java.util.Collections.reverse(l);
                    return l;
                }));
    }

    public MessageReactionDTO addReaction(User user, Long messageId, String emoji, Conversation conversation) {
        messageValidationService.validateDuplicateEmojiForMessage(user, messageId, emoji);
        MessageReaction reaction = messageActions.createMessageReaction(user, getById(messageId), emoji);
        return MessageReactionDTO.from(reaction, conversation.getId());
    }

    @Transactional
    public MessageReactionDTO removeReaction(User user, Long messageId, String emoji, Conversation conversation) {
        MessageReaction reaction = messageReactionRepository
                .findByMessageIdAndUserIdAndEmoji(messageId, user.getId(), emoji)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        MessageReactionDTO dto = MessageReactionDTO.from(reaction, conversation.getId());
        messageReactionRepository.delete(reaction);
        return dto;
    }

    private Map<Long, List<MessageReactionDTO>> loadReactionsForMessages(List<Long> messageIds, Long conversationId) {
        if (messageIds.isEmpty()) return Map.of();
        return messageReactionRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(
                        r -> r.getMessage().getId(),
                        Collectors.mapping(r -> MessageReactionDTO.from(r, conversationId), Collectors.toList())
                ));
    }

    private Map<Long, List<StoredFileDTO>> loadAttachmentsForMessages(List<Long> messageIds) {
        if (messageIds.isEmpty()) return Map.of();
        return storageLookup.findAttachmentsByMessageIds(messageIds);
    }

    private List<StoredFileDTO> attachmentsFor(Long messageId) {
        return loadAttachmentsForMessages(List.of(messageId)).getOrDefault(messageId, List.of());
    }

    public Message getById(Long messageId) {
        return messageRepository.findById(messageId).orElseThrow();
    }

    private Long getMarginId(Conversation conversation) {
        if (conversation.getChannel() == null) return null;
        return conversation.getChannel().getSpace().getMargin().getId();
    }
}
