package org.margin.server.sfu.services;

import org.springframework.modulith.NamedInterface;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.margin.server.presence.PresenceService;
import org.margin.server.sfu.events.ChannelCallInviteEvent;
import org.margin.server.sfu.events.ChannelVoiceParticipantEvent;
import org.margin.server.sfu.events.MeetingPeerJoinedEvent;
import org.margin.server.sfu.events.MeetingPeerLeftEvent;
import org.margin.server.shared.voice.RoomKey;
import org.margin.server.sfu.models.ChannelCallInvitePayload;
import org.margin.server.sfu.models.ChannelVoiceParticipantPayload;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.api.UserLookup;
import org.margin.server.users.services.UserService;
import org.margin.server.sfu.models.VoiceParticipantChange;
import org.margin.server.shared.voice.VoiceParticipantLookup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@NamedInterface("api")
@Slf4j
@Service
public class SfuService implements VoiceParticipantLookup {

    private final ApplicationEventPublisher eventPublisher;
    private final PresenceService presenceService;
    private final UserService userService;
    private final UserLookup userLookup;

    @Getter
    @Value("${sfu.url:http://localhost:3000}")
    private String sfuUrl;
    @Getter
    @Value("${sfu.public-url:ws://localhost:3000}")
    private String sfuPublicUrl;
    @Value("${sfu.internal-api-key}")
    private String internalApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public SfuService(ApplicationEventPublisher eventPublisher, PresenceService presenceService, UserService userService, UserLookup userLookup) {
        this.eventPublisher = eventPublisher;
        this.presenceService = presenceService;
        this.userService = userService;
        this.userLookup = userLookup;
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
        ChannelVoiceParticipantPayload payload = new ChannelVoiceParticipantPayload(
                channelId, userLookup.dtoOf(userId)
        );
        eventPublisher.publishEvent(new ChannelVoiceParticipantEvent(channelId, VoiceParticipantChange.JOINED, payload));
    }

    public void inviteToChannelCall(Long inviterId, Long recipientId, Long channelId, String channelName) {
        ChannelCallInvitePayload payload = new ChannelCallInvitePayload(
                channelId, channelName, userLookup.dtoOf(inviterId)
        );
        eventPublisher.publishEvent(new ChannelCallInviteEvent(recipientId, payload));
    }

    public void notifyUserLeft(Long channelId, Long userId) {
        eventPublisher.publishEvent(new ChannelVoiceParticipantEvent(channelId, VoiceParticipantChange.LEFT,
                new ChannelVoiceParticipantPayload(channelId, userLookup.dtoOf(userId))));
    }

    public void notifyMeetingPeerJoined(String meetingCode, String peerId) {
        eventPublisher.publishEvent(new MeetingPeerJoinedEvent(meetingCode, peerId));
    }

    public void notifyMeetingPeerLeft(String meetingCode, String peerId) {
        eventPublisher.publishEvent(new MeetingPeerLeftEvent(meetingCode, peerId));
    }

    public List<UserDTO> getVoiceParticipants(Long channelId) {
        return userLookup.dtosOf(
                peerIdsInRoom(new RoomKey.ChannelRoom(channelId)).stream().map(Long::parseLong).toList()
        );
    }

    /**
     * Who the SFU currently has connected to a room. The SFU is the source of truth for presence
     * in a room; peer_joined/peer_left callbacks are fire-and-forget and can be dropped.
     */
    public List<String> peerIdsInRoom(RoomKey room) {
        String url = sfuUrl + "/rooms/" + room.value() + "/peers";
        try {
            ResponseEntity<Map<String, List<String>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            return response.getBody().getOrDefault("peers", List.of());
        } catch (Exception e) {
            log.warn("Could not fetch peers for room {}: {}", room.value(), e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<Long, List<Long>> participantIdsByChannel(Collection<Long> channelIds) {
        if (channelIds.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> peersByRoom = fetchAllRoomPeers();
        Map<Long, List<Long>> participants = new HashMap<>();

        for (Long channelId : channelIds) {
            List<String> peerIds = peersByRoom.get(String.valueOf(channelId));
            if (peerIds == null || peerIds.isEmpty()) {
                continue;
            }
            participants.put(channelId, peerIds.stream().map(Long::parseLong).toList());
        }

        return participants;
    }

    private Map<String, List<String>> fetchAllRoomPeers() {
        String url = sfuUrl + "/rooms/peers";
        try {
            ResponseEntity<Map<String, Map<String, List<String>>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    new ParameterizedTypeReference<>() {
                    }
            );
            Map<String, Map<String, List<String>>> body = response.getBody();
            return body == null ? Map.of() : body.getOrDefault("rooms", Map.of());
        } catch (Exception e) {
            log.warn("Could not fetch voice participants: {}", e.getMessage());
            return Map.of();
        }
    }

    public void validateInternalApiKey(String apiKey) {
        if (!internalApiKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}