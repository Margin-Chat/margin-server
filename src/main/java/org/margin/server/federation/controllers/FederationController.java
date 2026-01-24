package org.margin.server.federation.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.margin.server.social.communication.messages.models.DirectMessage;
import org.margin.server.config.FederationConfig;
import org.margin.server.federation.models.FederatedMessageDTO;
import org.margin.server.federation.models.FederationInfo;
import org.margin.server.users.UserService;
import org.margin.server.websocket.services.WebSocketClientService;

import java.util.Map;

@Slf4j
@RestController
public class FederationController {

    private final WebSocketClientService webSocketClientService;
    private final FederationConfig federationConfig;
    private final UserService userService;


    public FederationController(WebSocketClientService webSocketClientService, FederationConfig federationConfig, UserService userService) {
        this.webSocketClientService = webSocketClientService;
        this.federationConfig = federationConfig;
        this.userService = userService;
    }

    /**
     * Receive messages from other servers
     */
    @PostMapping("/federation/messages")
    public ResponseEntity<Void> receiveMessage(
            @RequestBody FederatedMessageDTO messageDTO,
            @RequestHeader("X-Server-Domain") String originServer) {

        log.info("Received federated message from {} to user {}",
                originServer, messageDTO.getToUserId());

        // TODO: Add server authentication/verification here

        DirectMessage message = new DirectMessage(
                messageDTO.getFromUserId(),
                messageDTO.getToUserId(),
                messageDTO.getFromServer(),
                federationConfig.getServerDomain(),
                messageDTO.getMessage()
        );

        webSocketClientService.sendMessageToUser(message);

        return ResponseEntity.ok().build();
    }

    /**
     * Well-known endpoint for server discovery
     */
    @GetMapping("/.well-known/federation")
    public FederationInfo getFederationInfo() {
        return FederationInfo.builder()
                .serverDomain(federationConfig.getServerDomain())
                .version("1.0")
                .endpoints(Map.of(
                        "messages", federationConfig.getPublicEndpoint() + "/federation/messages"
                ))
                .build();
    }
}