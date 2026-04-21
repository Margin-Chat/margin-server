package org.margin.server.social.messages.services;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationInviteStatus;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.conversation.repositories.ConversationMemberRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final ConversationService conversationService;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConnectionManager connectionManager;
    private final WebSocketDeliveryService webSocketDeliveryService;

    public MessageService(MessageRepository messageRepository,
                          MessageReactionRepository messageReactionRepository,
                          ConversationService conversationService,
                          ConversationMemberRepository conversationMemberRepository, ConnectionManager connectionManager,
                          WebSocketDeliveryService webSocketDeliveryService) {
        this.messageRepository = messageRepository;
        this.messageReactionRepository = messageReactionRepository;
        this.conversationService = conversationService;
        this.conversationMemberRepository = conversationMemberRepository;
        this.connectionManager = connectionManager;
        this.webSocketDeliveryService = webSocketDeliveryService;
    }

    @Transactional
    public MessageResult createMessage(User fromUser, Conversation conversation, String content) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setFromUser(fromUser);
        message.setMessage(content);
        message.setCreatedAt(Instant.now());

        message = messageRepository.save(message);

        List<User> recipients = conversationService.getConversationMembers(conversation.getId());

        return new MessageResult(
                MessageDTO.from(message)
                        .withOnline(connectionManager.isUserOnline(message.getFromUser().getId()))
                        .withMarginId(getMarginId(conversation))
                        .withChannelName(conversation.getChannel() == null ? null : conversation.getChannel().getName())
                        .build(),
                recipients);
    }

    @Transactional
    public MessageResult editMessage(Conversation conversation, Long messageId, String content) {
        Message message = getById(messageId);
        message.setMessage(content);
        message.setIsEdited(true);
        messageRepository.save(message);

        List<User> recipients = conversationService.getConversationMembers(conversation.getId());

        return new MessageResult(
                MessageDTO.from(message)
                        .withOnline(connectionManager.isUserOnline(message.getFromUser().getId()))
                        .withMarginId(getMarginId(conversation))
                        .build(),
                recipients);
    }

    @Transactional
    public MessageResult deleteMessage(Long messageId, Conversation conversation) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        MessageDTO messageDTO = MessageDTO.from(message)
                .withOnline(connectionManager.isUserOnline(message.getFromUser().getId()))
                .withMarginId(getMarginId(conversation))
                .build();

        message.setIsDeleted(true);
        messageRepository.delete(message);

        List<User> recipients = conversationService.getConversationMembers(conversation.getId());

        return new MessageResult(messageDTO, recipients);
    }

    @Transactional
    public void sendMessage(User fromUser, String content, Conversation conversation) {
        if (conversation.getType() == ConversationType.DIRECT) {
            boolean anyPending = conversationMemberRepository.findByConversation(conversation).stream()
                    .anyMatch(m -> !m.getUser().getId().equals(fromUser.getId())
                            && m.getInviteStatus() == ConversationInviteStatus.PENDING);
            if (anyPending) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Cannot send messages until the invite is accepted");
            }
        }
        MessageResult result = createMessage(fromUser, conversation, content);
        webSocketDeliveryService.notifyMessage(
                result.message(),
                result.recipients(),
                result.message().conversationType()
        );
    }

    @Transactional(readOnly = true)
    public List<MessageDTO> getConversationMessages(Conversation conversation, int limit, Long before,
                                                    Long currentUserId, Instant otherUserLastReadAt) {
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
                                .build())
                .collect(Collectors.collectingAndThen(Collectors.toList(), l -> {
                    java.util.Collections.reverse(l);
                    return l;
                }));
    }

    @Transactional(readOnly = true)
    public List<MessageDTO> getConversationMessages(Conversation conversation, int limit, Long before) {
        return getConversationMessages(conversation, limit, before, null, Instant.EPOCH);
    }

    @Transactional
    public MessageReactionDTO addReaction(User user, Long messageId, String emoji, Conversation conversation) {
        if (messageReactionRepository.existsByMessageIdAndUserIdAndEmoji(messageId, user.getId(), emoji)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reaction already exists");
        }
        Message message = getById(messageId);
        MessageReaction reaction = messageReactionRepository.save(new MessageReaction(message, user, emoji));
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