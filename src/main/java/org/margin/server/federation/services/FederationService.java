package org.margin.server.federation.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.margin.server.social.communication.messages.models.DirectMessage;
import org.margin.server.config.FederationConfig;
import org.margin.server.federation.models.FederatedMessageDTO;
import org.margin.server.federation.models.FederationInfo;

@Slf4j
@Service
public class FederationService {
    private final FederationConfig federationConfig;
    private RestTemplate restTemplate;

    public FederationService(FederationConfig federationConfig) {
        this.federationConfig = federationConfig;
    }

    /**
     * Send a message to a user on another server
     */
    public boolean sendFederatedMessage(DirectMessage message) {
        try {
            String targetServerUrl = discoverServerEndpoint(message.getToUserServer());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Server-Domain", federationConfig.getServerDomain());
            // TODO: Add authentication

            HttpEntity<FederatedMessageDTO> request = new HttpEntity<>(
                    toFederatedDTO(message), headers
            );

            String endpoint = targetServerUrl + "/federation/messages";
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    endpoint, request, Void.class
            );

            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            log.error("Failed to send federated message to {}: {}",
                    message.getToUserServer(), e.getMessage());
            return false;
        }
    }

    /**
     * Discover the endpoint for a given server domain
     */
    private String discoverServerEndpoint(String serverDomain) {
        // Try well-known endpoint first
        try {
            String wellKnownUrl = "https://" + serverDomain + "/.well-known/federation";
            FederationInfo info = restTemplate.getForObject(wellKnownUrl, FederationInfo.class);
            if (info != null && info.getEndpoints() != null) {
                return info.getEndpoints().get("messages");
            }
        } catch (Exception e) {
            log.warn("Could not discover server info for {}, using default", serverDomain);
        }

        // Fallback to standard endpoint
        return "https://" + serverDomain;
    }

    private FederatedMessageDTO toFederatedDTO(DirectMessage message) {
        return FederatedMessageDTO.builder()
                .fromUserId(message.getFromUserId())
                .fromServer(message.getFromUserServer() != null ?
                        message.getFromUserServer() : federationConfig.getServerDomain())
                .toUserId(message.getToUserId())
                .message(message.getMessage())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}