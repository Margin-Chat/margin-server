package org.margin.server.social.services;

import org.margin.server.social.models.DirectMessage;
import org.margin.server.social.models.SpaceChannelMessage;
import org.margin.server.social.repositories.DirectChatMessageRepository;
import org.margin.server.social.repositories.SpaceChannelMessageRepository;
import org.springframework.stereotype.Service;

import java.util.Date;

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
}
