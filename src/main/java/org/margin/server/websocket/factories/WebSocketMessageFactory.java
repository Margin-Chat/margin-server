package org.margin.server.websocket.factories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.margin.server.social.models.SpaceChannelMessage;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.stereotype.Component;
import org.margin.server.social.models.DirectMessage;
import org.margin.server.users.models.User;
import org.margin.server.websocket.models.WebSocketMessage;

@Component
public class WebSocketMessageFactory {
	private final ObjectMapper mapper;

    public WebSocketMessageFactory(ObjectMapper mapper) {
		this.mapper = mapper;
    }

	public String createUserActivity(WebSocketMessageType type, User user) throws JsonProcessingException {
		WebSocketMessage message = new WebSocketMessage(
				type,
				user.getId(),
				System.currentTimeMillis(), null);
		return mapper.writeValueAsString(message);
	}

	public String createWebSocketChatMessage(DirectMessage directMessage) throws JsonProcessingException {
		WebSocketMessage message = new WebSocketMessage(WebSocketMessageType.RECEIVE_DIRECT_MESSAGE,
				directMessage.getToUserId(),
                System.currentTimeMillis(),
				mapper.writeValueAsString(directMessage));
		return mapper.writeValueAsString(message);
	}

    public String createWebSocketChannelMessage(SpaceChannelMessage channelMessage) throws JsonProcessingException {
        WebSocketMessage message = new WebSocketMessage(WebSocketMessageType.RECEIVE_CHANNEL_MESSAGE,
                channelMessage.getChannelId(),
                System.currentTimeMillis(),
                mapper.writeValueAsString(channelMessage));
        return mapper.writeValueAsString(message);
    }

    public String createWebSocketCallMessage(User fromUser, String offer, WebSocketMessageType callType) throws JsonProcessingException {
        WebSocketMessage message = new WebSocketMessage(
                callType,
                fromUser.getId(),
                System.currentTimeMillis(),
                offer);
        return mapper.writeValueAsString(message);
    }
}
