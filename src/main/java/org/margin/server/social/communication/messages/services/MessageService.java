package org.margin.server.social.communication.messages.services;

import jakarta.transaction.Transactional;
import org.margin.server.social.communication.messages.models.DirectMessage;
import org.margin.server.social.communication.messages.models.SpaceChannelMessage;
import org.margin.server.social.communication.messages.repositories.DirectChatMessageRepository;
import org.margin.server.social.communication.messages.repositories.SpaceChannelMessageRepository;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class MessageService {
	private final DirectChatMessageRepository directChatMessageRepository;
    private final SpaceChannelMessageRepository spaceChannelMessageRepository;

	public MessageService(DirectChatMessageRepository directChatMessageRepository,
                          SpaceChannelMessageRepository spaceChannelMessageRepository) {
		this.directChatMessageRepository = directChatMessageRepository;
        this.spaceChannelMessageRepository = spaceChannelMessageRepository;
	}

	public void saveDirectMessage(DirectMessage message) {
		message.setCreatedAt(new Date());
		directChatMessageRepository.save(message);
	}

    public void saveChannelMessage(SpaceChannelMessage message) {
        message.setCreatedAt(new Date());
        spaceChannelMessageRepository.save(message);
    }

	@Transactional
	public void setMessagesToRead(Long fromUserId, Long toUserId, List<Long> messageIds) {
		directChatMessageRepository.setMessagesToRead(fromUserId, toUserId, messageIds);
	}
}
