package org.margin.server.social.messages.services;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.messages.models.dtos.MessageResult;
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
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationService conversationService;
    private final ConnectionManager connectionManager;
    private final WebSocketDeliveryService webSocketDeliveryService;

    public MessageService(MessageRepository messageRepository,
                          ConversationService conversationService, ConnectionManager connectionManager, WebSocketDeliveryService webSocketDeliveryService) {
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
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

        return new MessageResult(new MessageDTO(
                message,
                conversation.getType(),
                connectionManager.isUserOnline(message.getFromUser().getId()),
                getMarginId(conversation)),
                recipients);
    }

    @Transactional
    public MessageResult editMessage(Conversation conversation, Long messageId, String content) {
        Message message = getMessage(messageId);
        message.setMessage(content);
        message.setIsEdited(true);
        messageRepository.save(message);

        List<User> recipients = conversationService.getConversationMembers(conversation.getId());

        return new MessageResult(new MessageDTO(
                message,
                conversation.getType(),
                connectionManager.isUserOnline(message.getFromUser().getId()),
                getMarginId(conversation)),
                recipients);

    }

    @Transactional
    public MessageResult deleteMessage(Long messageId, Conversation conversation) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        MessageDTO messageDTO = new MessageDTO(
                message,
                conversation.getType(),
                connectionManager.isUserOnline(message.getFromUser().getId()),
                getMarginId(conversation)
        );

        messageRepository.delete(message);

        List<User> recipients = conversationService.getConversationMembers(conversation.getId());

        return new MessageResult(messageDTO, recipients);
    }

    @Transactional
    public void sendMessage(User fromUser, String content, Conversation conversation) {
        MessageResult result = createMessage(fromUser, conversation, content);
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

        return messages.stream()
                .map(msg -> new MessageDTO(
                        msg,
                        msg.getConversation().getType(),
                        connectionManager.isUserOnline(msg.getFromUser().getId()),
                        getMarginId(conversation)))
                .collect(Collectors.collectingAndThen(Collectors.toList(), l -> {
                    java.util.Collections.reverse(l);
                    return l;
                }));
    }

    public Message getMessage(Long messageId) {
        return messageRepository.findById(messageId).orElseThrow();
    }

    private Long getMarginId(Conversation conversation) {
        if (conversation.getChannel() == null) return null;
        return conversation.getChannel().getSpace().getMargin().getId();
    }
}