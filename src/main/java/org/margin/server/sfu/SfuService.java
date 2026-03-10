package org.margin.server.sfu;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.margin.server.sfu.models.ChannelVoiceParticipantPayload;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class SfuService {

    private final WebSocketDeliveryService webSocketDeliveryService;
    private final ConnectionManager connectionManager;
    private final UserService userService;
    private final UserRepository userRepository;
    @Value("${mediasoup.url:http://localhost:3000}")
    private String mediasoupUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public SfuService(WebSocketDeliveryService webSocketDeliveryService, ConnectionManager connectionManager, UserService userService, UserRepository userRepository) {
        this.webSocketDeliveryService = webSocketDeliveryService;
        this.connectionManager = connectionManager;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    public String createOrJoinRoom(String roomId) {
        String url = mediasoupUrl +
                "/rooms/" +
                roomId;

        restTemplate.exchange(
                url,
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        return mediasoupUrl;
    }

    public void notifyUserJoined(Long channelId, User user) {
        ChannelVoiceParticipantPayload payload = new ChannelVoiceParticipantPayload(
                channelId, new UserDTO(user, connectionManager.isUserOnline(user.getId()))
        );
        webSocketDeliveryService.notifySpaceMembersByChannelId(channelId, WebSocketMessageType.USER_JOINED_VOICE, payload);
    }

    public void notifyUserLeft(Long channelId, Long userId) {
        webSocketDeliveryService.notifySpaceMembersByChannelId(channelId, WebSocketMessageType.USER_LEFT_VOICE,
                new ChannelVoiceParticipantPayload(channelId, userService.toDTO(userService.getById(userId))));
    }

    public List<UserDTO> getVoiceParticipants(Long channelId) {
        String url = mediasoupUrl + "/rooms/" + channelId + "/peers";
        try {
            Map<String, List<String>> response = restTemplate.getForObject(url, Map.class);
            List<String> peerIds = response.getOrDefault("peers", List.of());
            return peerIds.stream()
                    .map(id -> userRepository.findById(Long.parseLong(id)).orElse(null))
                    .filter(Objects::nonNull)
                    .map(u -> new UserDTO(u, connectionManager.isUserOnline(u.getId())))
                    .toList();
        } catch (Exception e) {
            log.warn("Could not fetch voice participants for channel {}: {}", channelId, e.getMessage());
            return List.of();
        }
    }
}