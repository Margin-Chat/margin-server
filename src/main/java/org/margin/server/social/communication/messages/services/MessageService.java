package org.margin.server.social.communication.messages.services;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.notifications.NotificationService;
import org.margin.server.social.communication.exceptions.ChannelNotFoundException;
import org.margin.server.social.communication.messages.models.ChannelMessageResult;
import org.margin.server.social.communication.messages.models.DirectMessage;
import org.margin.server.social.communication.messages.models.SpaceChannelMessage;
import org.margin.server.social.communication.messages.models.dtos.DirectMessageDTO;
import org.margin.server.social.communication.messages.models.dtos.SpaceChannelMessageDTO;
import org.margin.server.social.communication.messages.repositories.DirectChatMessageRepository;
import org.margin.server.social.communication.messages.repositories.SpaceChannelMessageRepository;
import org.margin.server.social.models.SpaceChannel;
import org.margin.server.social.repositories.SpaceChannelRepository;
import org.margin.server.social.repositories.SpaceRepository;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.services.UserService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class MessageService {

	private final DirectChatMessageRepository directChatMessageRepository;
	private final SpaceChannelMessageRepository spaceChannelMessageRepository;
	private final SpaceChannelRepository channelRepository;
	private final SpaceRepository spaceRepository;
	private final UserService userService;

	public MessageService(DirectChatMessageRepository directChatMessageRepository,
						  SpaceChannelMessageRepository spaceChannelMessageRepository,
						  SpaceChannelRepository channelRepository,
						  SpaceRepository spaceRepository,
						  UserService userService) {
		this.directChatMessageRepository = directChatMessageRepository;
		this.spaceChannelMessageRepository = spaceChannelMessageRepository;
		this.channelRepository = channelRepository;
		this.spaceRepository = spaceRepository;
		this.userService = userService;
	}

	@Transactional
	public DirectMessageDTO sendDirectMessage(Long fromUserId, Long toUserId, String content) {
		DirectMessage message = new DirectMessage(fromUserId, toUserId, content);
		message.setCreatedAt(new Date());

		DirectMessage saved = directChatMessageRepository.save(message);
		log.info("Direct message saved: {} -> {}", fromUserId, toUserId);

		return DirectMessageDTO.fromEntity(saved);
	}

	@Transactional
	public ChannelMessageResult sendChannelMessage(Long fromUserId, Long channelId, String content) {
		SpaceChannel channel = channelRepository.findById(channelId)
				.orElseThrow(() -> new ChannelNotFoundException(channelId));

		SpaceChannelMessage message = new SpaceChannelMessage(
				fromUserId,
				channelId,
				channel.getSpaceId(),
				content
		);
		message.setCreatedAt(new Date());

		SpaceChannelMessage saved = spaceChannelMessageRepository.save(message);
		SpaceChannelMessageDTO dto = SpaceChannelMessageDTO.fromEntity(saved, userService.getById(fromUserId).getUsername());
		List<User> recipients = spaceRepository.getUsersForSpace(channel.getSpaceId());

		return new ChannelMessageResult(dto, recipients);
	}

	@Transactional
	public void setMessagesToRead(Long fromUserId, Long toUserId, List<Long> messageIds) {
		directChatMessageRepository.setMessagesToRead(fromUserId, toUserId, messageIds);
	}

}