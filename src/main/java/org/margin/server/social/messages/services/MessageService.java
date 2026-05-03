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
    private final ConversationService conversationService;
    private final ConnectionManager connectionManager;
    private final WebSocketDeliveryService webSocketDeliveryService;
    private final MessageActions messageActions;
    private final MessageValidationService messageValidationService;

    public MessageService(MessageRepository messageRepository,
                          MessageReactionRepository messageReactionRepository,
                          ConversationService conversationService,
                          ConnectionManager connectionManager,
                          WebSocketDeliveryService webSocketDeliveryService,
                          MessageActions messageActions,
                          MessageValidationService messageValidationService) {
        this.messageRepository = messageRepository;
        this.messageReactionRepository = messageReactionRepository;
        this.conversationService = conversationService;
        this.connectionManager = connectionManager;
        this.webSocketDeliveryService = webSocketDeliveryService;
        this.messageActions = messageActions;
        this.messageValidationService = messageValidationService;
    }

    public MessageResult createMessageForUsers(User fromUser, Conversation conversation, String content, String imageAddress) {
        Message message = messageActions.createMessage(fromUser, conversation, content, imageAddress);
        List<User> recipients = conversationService.getConversationMembers(conversation.getId());
        return new MessageResult(
                MessageDTO.from(message)
                        .withOnline(connectionManager.isUserOnline(message.getFromUser().getId()))
                        .withMarginId(getMarginId(conversation))
                        .withChannelName(conversation.getChannel() == null ? null : conversation.getChannel().getName())
                        .withImageAddress(message.getImageAddress())
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

    public void sendMessage(User fromUser, String content, Conversation conversation, String imageAddress) {
        messageValidationService.validateConversationIsNotPending(fromUser, conversation);
        MessageResult result = createMessageForUsers(fromUser, conversation, content, imageAddress);
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

        return messages.stream()
                .map(msg ->
                        MessageDTO.from(msg)
                                .withOnline(connectionManager.isUserOnline(msg.getFromUser().getId()))
                                .withMarginId(getMarginId(conversation))
                                .withReactions(reactionsByMessageId.getOrDefault(msg.getId(), List.of()))
                                .withImageAddress(msg.getImageAddress())
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

    public Message getById(Long messageId) {
        return messageRepository.findById(messageId).orElseThrow();
    }

    private Long getMarginId(Conversation conversation) {
        if (conversation.getChannel() == null) return null;
        return conversation.getChannel().getSpace().getMargin().getId();
    }
}