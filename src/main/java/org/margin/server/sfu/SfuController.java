package org.margin.server.sfu;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.sfu.models.PeerJoinedRequest;
import org.margin.server.sfu.models.PeerLeftRequest;
import org.margin.server.sfu.models.SfuJoinResponse;
import org.margin.server.sfu.services.SfuService;
import org.margin.server.sfu.services.SfuTokenService;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.channel.services.ChannelService;
import org.margin.server.social.margin.validations.MarginAuthorizationService;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/sfu")
public class SfuController {
    private final SfuService sfuService;
    private final MarginAuthorizationService marginAuthorizationService;
    private final SfuTokenService sfuTokenService;
    private final ChannelService channelService;
    private final SubscriptionValidationService subscriptionValidationService;

    public SfuController(SfuService sfuService,
                         MarginAuthorizationService marginAuthorizationService,
                         SfuTokenService sfuTokenService,
                         ChannelService channelService,
                         SubscriptionValidationService subscriptionValidationService) {
        this.sfuService = sfuService;
        this.marginAuthorizationService = marginAuthorizationService;
        this.sfuTokenService = sfuTokenService;
        this.channelService = channelService;
        this.subscriptionValidationService = subscriptionValidationService;
    }

    @PostMapping("create_or_join")
    public ResponseEntity<SfuJoinResponse> getConnectionInfo(@RequestParam String roomId,
                                                             @AuthenticationPrincipal User user) {
        Long channelId = Long.parseLong(roomId);
        marginAuthorizationService.requireChannelMember(user.getId(), channelId);
        Channel channel = channelService.getById(channelId);
        int currentParticipants = sfuService.getVoiceParticipants(channelId).size();
        int maxParticipants = subscriptionValidationService.validateChannelVoiceJoin(channel, currentParticipants);
        sfuService.createOrJoinRoom(roomId, maxParticipants);
        String roomToken = sfuTokenService.generateRoomToken(user.getId(), roomId);
        return ResponseEntity.ok(new SfuJoinResponse(sfuService.getSfuPublicUrl(), roomToken));
    }

    @PostMapping("peer_joined")
    public ResponseEntity<Void> peerJoined(@RequestBody PeerJoinedRequest request,
                                           @RequestHeader("X-Internal-Api-Key") String apiKey) {
        sfuService.validateInternalApiKey(apiKey);
        sfuService.notifyUserJoined(Long.parseLong(request.roomId()), Long.parseLong(request.peerId()));
        return ResponseEntity.ok().build();
    }

    @PostMapping("peer_left")
    public ResponseEntity<Void> peerLeft(@RequestBody PeerLeftRequest request,
                                         @RequestHeader("X-Internal-Api-Key") String apiKey) {
        sfuService.validateInternalApiKey(apiKey);
        sfuService.notifyUserLeft(Long.parseLong(request.roomId()), Long.parseLong(request.peerId()));
        return ResponseEntity.ok().build();
    }

    @GetMapping("voice_participants/{channelId}")
    public ResponseEntity<List<UserDTO>> getVoiceParticipants(@PathVariable Long channelId,
                                                              @AuthenticationPrincipal User user) {
        marginAuthorizationService.requireChannelMember(user.getId(), channelId);
        return ResponseEntity.ok(sfuService.getVoiceParticipants(channelId));
    }
}