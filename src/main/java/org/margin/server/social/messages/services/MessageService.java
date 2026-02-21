package org.margin.server.social.messages.services;

import org.margin.server.social.conversation.Conversation;
import org.margin.server.social.conversation.services.ConversationService;
import org.margin.server.social.messages.models.Message;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.messages.models.dtos.MessageResult;
import org.margin.server.social.messages.repositories.MessageRepository;
import org.margin.server.users.models.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageService {

	private final MessageRepository messageRepository;
	private final ConversationService conversationService;

	public MessageService(MessageRepository messageRepository,
						  ConversationService conversationService) {
		this.messageRepository = messageRepository;
		this.conversationService = conversationService;
	}

	@Transactional
	public MessageResult sendMessage(User fromUser, Conversation conversation, String content) {
		Message message = new Message();
		message.setConversation(conversation);
		message.setFromUser(fromUser);
		message.setMessage(content);
		message.setCreatedAt(LocalDateTime.now());

		message = messageRepository.save(message);

		List<User> recipients = conversationService.getConversationMembers(conversation.getId());

		return new MessageResult(new MessageDTO(message, conversation.getType()), recipients);
	}

	@Transactional
	public MessageResult sendDirectMessage(User fromUser, User toUser, String content) {
		Conversation conversation = conversationService.findOrCreateDirectConversation(fromUser, toUser);
		return sendMessage(fromUser, conversation, content);
	}

	public List<MessageDTO> getConversationMessages(Long conversationId, int limit) {
		return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, PageRequest.of(0, limit))
				.stream()
				.map(msg -> new MessageDTO(msg, msg.getConversation().getType()))
				.collect(Collectors.toList());
	}
}