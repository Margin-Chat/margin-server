package org.margin.server.sfu;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.sfu.models.PeerJoinedRequest;
import org.margin.server.sfu.models.PeerLeftRequest;
import org.margin.server.sfu.models.SfuJoinResponse;
import org.margin.server.sfu.services.SfuService;
import org.margin.server.sfu.services.SfuTokenService;
import org.margin.server.social.api.ChannelLookup;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.subscriptions.models.SubscriptionTier;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
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
    private final MarginAccessChecker marginAccessChecker;
    private final SfuTokenService sfuTokenService;
    private final ChannelLookup channelLookup;
    private final SubscriptionValidationService subscriptionValidationService;

    public SfuController(SfuService sfuService,
                         MarginAccessChecker marginAccessChecker,
                         SfuTokenService sfuTokenService,
                         ChannelLookup channelLookup,
                         SubscriptionValidationService subscriptionValidationService) {
        this.sfuService = sfuService;
        this.marginAccessChecker = marginAccessChecker;
        this.sfuTokenService = sfuTokenService;
        this.channelLookup = channelLookup;
        this.subscriptionValidationService = subscriptionValidationService;
    }

    @PostMapping("create_or_join")
    public ResponseEntity<SfuJoinResponse> getConnectionInfo(@RequestParam String roomId,
                                                             @AuthenticationPrincipal AuthenticatedUser user) {
        Long channelId = Long.parseLong(roomId);
        marginAccessChecker.requireChannelMember(user.id(), channelId);
        int currentParticipants = sfuService.getVoiceParticipants(channelId).size();
        int maxParticipants = subscriptionValidationService.validateChannelVoiceJoin(channelLookup.marginIdOf(channelId), currentParticipants);
        sfuService.createOrJoinRoom(roomId, maxParticipants);
        String roomToken = sfuTokenService.generateRoomToken(user.id(), roomId);
        boolean isFree = subscriptionValidationService
                .tierForMargin(channelLookup.marginIdOf(channelId)) == SubscriptionTier.FREE;
        Integer maxVideoHeight = isFree ? 720 : null;
        return ResponseEntity.ok(new SfuJoinResponse(sfuService.getSfuPublicUrl(), roomToken, maxVideoHeight));
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
                                                              @AuthenticationPrincipal AuthenticatedUser user) {
        marginAccessChecker.requireChannelMember(user.id(), channelId);
        return ResponseEntity.ok(sfuService.getVoiceParticipants(channelId));
    }
}