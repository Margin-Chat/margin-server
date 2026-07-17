package org.margin.server.notifications.push;

import org.margin.server.users.models.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push")
public class PushTokenController {

    public record PushTokenRequest(PushPlatform platform, String token) {
    }

    private final PushTokenService pushTokenService;

    public PushTokenController(PushTokenService pushTokenService) {
        this.pushTokenService = pushTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody PushTokenRequest request,
                                         @AuthenticationPrincipal User user) {
        pushTokenService.register(user, request.platform(), request.token());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/unregister")
    public ResponseEntity<Void> unregister(@RequestBody PushTokenRequest request,
                                           @AuthenticationPrincipal User user) {
        pushTokenService.unregister(user, request.token());
        return ResponseEntity.ok().build();
    }
}
