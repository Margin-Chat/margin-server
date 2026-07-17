package org.margin.server.notifications.push.senders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.margin.server.notifications.push.PushMessage;
import org.margin.server.notifications.push.PushPlatform;
import org.margin.server.notifications.push.PushSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

@Component
public class FcmPushSender implements PushSender {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String serviceAccountFile;
    private final HttpClient http = HttpClient.newHttpClient();

    private volatile ServiceAccount account;
    private volatile String accessToken;
    private volatile Instant accessTokenExpiry = Instant.EPOCH;

    private record ServiceAccount(String projectId, String clientEmail, PrivateKey key, String tokenUri) {
    }

    public FcmPushSender(@Value("${push.fcm.service-account-file:}") String serviceAccountFile) {
        this.serviceAccountFile = serviceAccountFile;
    }

    @Override
    public PushPlatform platform() {
        return PushPlatform.ANDROID;
    }

    @Override
    public boolean isEnabled() {
        return !serviceAccountFile.isBlank();
    }

    @Override
    public void send(String token, PushMessage message) {
        try {
            ObjectNode notification = objectMapper.createObjectNode()
                    .put("title", message.title())
                    .put("body", message.body());
            ObjectNode data = objectMapper.createObjectNode();
            message.data().forEach(data::put);
            ObjectNode fcmMessage = objectMapper.createObjectNode();
            fcmMessage.put("token", token);
            fcmMessage.set("notification", notification);
            fcmMessage.set("data", data);
            fcmMessage.set("android", objectMapper.createObjectNode().put("priority", "high"));
            ObjectNode body = objectMapper.createObjectNode();
            body.set("message", fcmMessage);

            ServiceAccount sa = loadAccount();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://fcm.googleapis.com/v1/projects/" + sa.projectId() + "/messages:send"))
                    .header("Authorization", "Bearer " + currentAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404 || response.body().contains("UNREGISTERED")) {
                throw new InvalidTokenException("FCM token unregistered");
            }
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("FCM send failed: HTTP " + response.statusCode() + " " + response.body());
            }
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("FCM send failed", e);
        }
    }

    private ServiceAccount loadAccount() throws Exception {
        if (account == null) {
            JsonNode json = objectMapper.readTree(Files.readString(Path.of(serviceAccountFile)));
            account = new ServiceAccount(
                    json.get("project_id").asText(),
                    json.get("client_email").asText(),
                    PemKeys.parsePkcs8(json.get("private_key").asText(), "RSA"),
                    json.get("token_uri").asText()
            );
        }
        return account;
    }

    private synchronized String currentAccessToken() throws Exception {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiry)) {
            return accessToken;
        }
        ServiceAccount sa = loadAccount();
        long now = Instant.now().getEpochSecond();
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = base64Url(objectMapper.createObjectNode()
                .put("iss", sa.clientEmail())
                .put("scope", SCOPE)
                .put("aud", sa.tokenUri())
                .put("iat", now)
                .put("exp", now + 3600)
                .toString().getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(sa.key());
        signature.update((header + "." + claims).getBytes(StandardCharsets.UTF_8));
        String assertion = header + "." + claims + "." + base64Url(signature.sign());

        String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
        HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create(sa.tokenUri()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = http.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("FCM OAuth token exchange failed: HTTP " + response.statusCode());
        }
        JsonNode json = objectMapper.readTree(response.body());
        accessToken = json.get("access_token").asText();
        accessTokenExpiry = Instant.now().plusSeconds(json.get("expires_in").asLong() - 60);
        return accessToken;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
