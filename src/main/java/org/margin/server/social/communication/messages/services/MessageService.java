package org.margin.server.social.communication.messages.services;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.social.communication.messages.models.dtos.ChannelMessageResult;
import org.margin.server.social.communication.messages.models.DirectMessage;
import org.margin.server.social.communication.messages.models.ChannelMessage;
import org.margin.server.social.communication.messages.models.dtos.DirectMessageDTO;
import org.margin.server.social.communication.messages.models.dtos.ChannelMessageDTO;
import org.margin.server.social.communication.messages.repositories.DirectChatMessageRepository;
import org.margin.server.social.communication.messages.repositories.ChannelMessageRepository;
import org.margin.server.social.models.channel.Channel;
import org.margin.server.social.repositories.ChannelRepository;
import org.margin.server.social.repositories.SpacesRepository;
import org.margin.server.users.models.User;
import org.margin.server.users.services.UserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class MessageService {

	private final DirectChatMessageRepository directChatMessageRepository;
	private final ChannelMessageRepository channelMessageRepository;
	private final ChannelRepository channelRepository;
	private final SpacesRepository spacesRepository;
	private final UserService userService;

	public MessageService(DirectChatMessageRepository directChatMessageRepository,
						  ChannelMessageRepository channelMessageRepository,
						  ChannelRepository channelRepository,
						  SpacesRepository spacesRepository,
						  UserService userService) {
		this.directChatMessageRepository = directChatMessageRepository;
		this.channelMessageRepository = channelMessageRepository;
		this.channelRepository = channelRepository;
		this.spacesRepository = spacesRepository;
		this.userService = userService;
	}

	@Transactional
	public DirectMessageDTO sendDirectMessage(User fromUser, User toUser, String content) {
		DirectMessage message = new DirectMessage(fromUser, toUser, content);
		message.setCreatedAt(new Date());

		DirectMessage saved = directChatMessageRepository.save(message);
		log.info("Direct message saved: {} -> {}", fromUser.getId(), toUser.getId());

		return DirectMessageDTO.fromEntity(saved);
	}

	@Transactional
	public ChannelMessageResult sendChannelMessage(User fromUser, Channel channel, String content) {
		ChannelMessage message = new ChannelMessage(
				fromUser,
				channel,
				content
		);
		message.setCreatedAt(new Date());

		ChannelMessage saved = channelMessageRepository.save(message);
		ChannelMessageDTO dto = ChannelMessageDTO.fromEntity(saved, fromUser.getUsername());
		List<User> recipients = spacesRepository.getUsersForSpace(channel.getSpace().getId());

		return new ChannelMessageResult(dto, recipients);
	}

	@Transactional
	public void setMessagesToRead(Long fromUserId, Long toUserId, List<Long> messageIds) {
		directChatMessageRepository.setMessagesToRead(fromUserId, toUserId, messageIds);
	}

    public List<DirectMessageDTO> getChatHistory(Long fromUserID, Long toUserId) {
		List<DirectMessage> messagesFromUser =
				directChatMessageRepository.findByFromUserIdAndToUserId(fromUserID, toUserId);
		List<DirectMessage> messagesToUser =
				directChatMessageRepository.findByFromUserIdAndToUserId(toUserId, fromUserID);

		List<DirectMessage> allMessages = new ArrayList<>();
		allMessages.addAll(messagesFromUser);
		allMessages.addAll(messagesToUser);

		allMessages.sort(Comparator.comparing(DirectMessage::getCreatedAt));

		return allMessages.stream().map(DirectMessageDTO::fromEntity).toList();
    }
}