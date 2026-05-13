package org.margin.server.subscriptions.services;

import lombok.extern.slf4j.Slf4j;
import org.margin.server.subscriptions.config.MollieProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class MollieClient {
    private final MollieProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public MollieClient(MollieProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> createCustomer(String name, String email) {
        return post("/customers", Map.of(
                "name", name,
                "email", email
        ));
    }

    public Map<String, Object> createFirstPayment(String customerId,
                                                  String amount,
                                                  String currency,
                                                  String description,
                                                  String redirectUrl,
                                                  String webhookUrl,
                                                  Map<String, String> metadata) {
        return post("/payments", Map.of(
                "amount", Map.of("currency", currency, "value", amount),
                "customerId", customerId,
                "sequenceType", "first",
                "description", description,
                "redirectUrl", redirectUrl,
                "webhookUrl", webhookUrl,
                "metadata", metadata
        ));
    }

    public Map<String, Object> createSubscription(String customerId,
                                                  String amount,
                                                  String currency,
                                                  String interval,
                                                  String description,
                                                  String webhookUrl,
                                                  String startDate) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "amount", Map.of("currency", currency, "value", amount),
                "interval", interval,
                "description", description,
                "webhookUrl", webhookUrl
        ));
        if (startDate != null) body.put("startDate", startDate);
        return post("/customers/" + customerId + "/subscriptions", body);
    }

    public Map<String, Object> getPayment(String paymentId) {
        return get("/payments/" + paymentId);
    }

    public Map<String, Object> getSubscription(String customerId, String subscriptionId) {
        return get("/customers/" + customerId + "/subscriptions/" + subscriptionId);
    }

    public void cancelSubscription(String customerId, String subscriptionId) {
        restTemplate.exchange(
                properties.apiBaseUrl() + "/customers/" + customerId + "/subscriptions/" + subscriptionId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }
        );
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.apiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> post(String path, Map<String, ?> body) {
        return restTemplate.exchange(
                properties.apiBaseUrl() + path,
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }
        ).getBody();
    }

    private Map<String, Object> get(String path) {
        return restTemplate.exchange(
                properties.apiBaseUrl() + path,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<Map<String, Object>>() {
                }
        ).getBody();
    }
}