package org.margin.server.sfu;

import org.margin.server.sfu.models.PeerLeftRequest;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sfu")
public class SfuController {
    private final SfuService sfuService;

    public SfuController(SfuService sfuService) {
        this.sfuService = sfuService;
    }

    @GetMapping("create_or_join")
    public ResponseEntity<String> getConnectionInfo(@RequestParam String roomId,
                                                    @AuthenticationPrincipal User user) {
        String mediasoupUrl = sfuService.createOrJoinRoom(roomId);
        sfuService.notifyUserJoined(Long.parseLong(roomId), user);
        return ResponseEntity.ok(mediasoupUrl);
    }

    @PostMapping("peer_left")
    public ResponseEntity<Void> peerLeft(@RequestBody PeerLeftRequest request) {
        sfuService.notifyUserLeft(Long.parseLong(request.roomId()), Long.parseLong(request.peerId()));
        return ResponseEntity.ok().build();
    }

    @GetMapping("voice_participants/{channelId}")
    public ResponseEntity<List<UserDTO>> getVoiceParticipants(@PathVariable Long channelId) {
        return ResponseEntity.ok(sfuService.getVoiceParticipants(channelId));
    }
}