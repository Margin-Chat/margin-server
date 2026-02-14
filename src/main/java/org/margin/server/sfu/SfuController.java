package org.margin.server.sfu;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/sfu")
public class SfuController {
    private final SfuService sfuService;

    public SfuController(SfuService sfuService) {
        this.sfuService = sfuService;
    }

    @GetMapping("create_or_join")
    public ResponseEntity<String> getConnectionInfo(@RequestParam String roomId) {
        String mediasoupUrl = sfuService.createOrJoinRoom(roomId);
        return ResponseEntity.ok(mediasoupUrl);
    }
}