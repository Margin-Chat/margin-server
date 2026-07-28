package org.margin.server.sfu.services;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.presence.PresenceService;
import org.margin.server.sfu.events.ChannelCallInviteEvent;
import org.margin.server.sfu.events.ChannelVoiceParticipantEvent;
import org.margin.server.sfu.models.ChannelCallInvitePayload;
import org.margin.server.sfu.models.ChannelVoiceParticipantPayload;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class SfuService {

    private final ApplicationEventPublisher eventPublisher;
    private final PresenceService presenceService;
    private final UserService userService;
    private final UserRepository userRepository;

    @Getter
    @Value("${sfu.url:http://localhost:3000}")
    private String sfuUrl;
    @Getter
    @Value("${sfu.public-url:ws://localhost:3000}")
    private String sfuPublicUrl;
    @Value("${sfu.internal-api-key}")
    private String internalApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public SfuService(ApplicationEventPublisher eventPublisher, PresenceService presenceService, UserService userService, UserRepository userRepository) {
        this.eventPublisher = eventPublisher;
        this.presenceService = presenceService;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", internalApiKey);
        return headers;
    }

    public void createOrJoinRoom(String roomId, int maxParticipants) {
        String url = sfuUrl + "/rooms/" + roomId;
        HttpHeaders headers = internalHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(Map.of("maxParticipants", maxParticipants), headers),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }
        );
    }

    public void notifyUserJoined(Long channelId, Long userId) {
        User user = userService.getById(userId);
        ChannelVoiceParticipantPayload payload = new ChannelVoiceParticipantPayload(
                channelId, new UserDTO(user, presenceService.isUserOnline(userId))
        );
        eventPublisher.publishEvent(new ChannelVoiceParticipantEvent(channelId, WebSocketMessageType.USER_JOINED_VOICE, payload));
    }

    public void inviteToChannelCall(User inviter, Long recipientId, Long channelId, String channelName) {
        ChannelCallInvitePayload payload = new ChannelCallInvitePayload(
                channelId, channelName, new UserDTO(inviter, presenceService.isUserOnline(inviter.getId()))
        );
        eventPublisher.publishEvent(new ChannelCallInviteEvent(recipientId, payload));
    }

    public void notifyUserLeft(Long channelId, Long userId) {
        eventPublisher.publishEvent(new ChannelVoiceParticipantEvent(channelId, WebSocketMessageType.USER_LEFT_VOICE,
                new ChannelVoiceParticipantPayload(channelId, userService.toDTO(userService.getById(userId)))));
    }

    public List<UserDTO> getVoiceParticipants(Long channelId) {
        String url = sfuUrl + "/rooms/" + channelId + "/peers";
        try {
            ResponseEntity<Map<String, List<String>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            List<String> peerIds = response.getBody().getOrDefault("peers", List.of());
            return peerIds.stream()
                    .map(id -> userRepository.findById(Long.parseLong(id)).orElse(null))
                    .filter(Objects::nonNull)
                    .map(u -> new UserDTO(u, presenceService.isUserOnline(u.getId())))
                    .toList();
        } catch (Exception e) {
            log.warn("Could not fetch voice participants for channel {}: {}", channelId, e.getMessage());
            return List.of();
        }
    }

    public void validateInternalApiKey(String apiKey) {
        if (!internalApiKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}