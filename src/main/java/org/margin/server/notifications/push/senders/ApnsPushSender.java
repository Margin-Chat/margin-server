package org.margin.server.notifications.push.senders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.margin.server.notifications.push.PushMessage;
import org.margin.server.notifications.push.PushPlatform;
import org.margin.server.notifications.push.PushSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Component
public class ApnsPushSender implements PushSender {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String keyFile;
    private final String keyId;
    private final String teamId;
    private final String bundleId;
    private final boolean production;
    private final HttpClient http = HttpClient.newHttpClient();

    private volatile String providerJwt;
    private volatile Instant providerJwtIssuedAt = Instant.EPOCH;

    public ApnsPushSender(@Value("${push.apns.key-file:}") String keyFile,
                          @Value("${push.apns.key-id:}") String keyId,
                          @Value("${push.apns.team-id:}") String teamId,
                          @Value("${push.apns.bundle-id:}") String bundleId,
                          @Value("${push.apns.production:false}") boolean production) {
        this.keyFile = keyFile;
        this.keyId = keyId;
        this.teamId = teamId;
        this.bundleId = bundleId;
        this.production = production;
    }

    @Override
    public PushPlatform platform() {
        return PushPlatform.IOS;
    }

    @Override
    public boolean isEnabled() {
        return !keyFile.isBlank() && !keyId.isBlank() && !teamId.isBlank() && !bundleId.isBlank();
    }

    @Override
    public void send(String token, PushMessage message) {
        try {
            ObjectNode alert = objectMapper.createObjectNode()
                    .put("title", message.title())
                    .put("body", message.body());
            ObjectNode aps = objectMapper.createObjectNode();
            aps.set("alert", alert);
            aps.put("sound", "default");
            ObjectNode payload = objectMapper.createObjectNode();
            payload.set("aps", aps);
            message.data().forEach(payload::put);

            String host = production ? "https://api.push.apple.com" : "https://api.sandbox.push.apple.com";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(host + "/3/device/" + token))
                    .header("authorization", "bearer " + providerJwt())
                    .header("apns-topic", bundleId)
                    .header("apns-push-type", "alert")
                    .header("apns-priority", "10")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 410
                    || response.body().contains("BadDeviceToken")
                    || response.body().contains("Unregistered")) {
                throw new InvalidTokenException("APNs token no longer valid");
            }
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("APNs send failed: HTTP " + response.statusCode() + " " + response.body());
            }
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("APNs send failed", e);
        }
    }

    private synchronized String providerJwt() throws Exception {
        if (providerJwt != null && Duration.between(providerJwtIssuedAt, Instant.now()).toMinutes() < 50) {
            return providerJwt;
        }
        PrivateKey key = PemKeys.parsePkcs8(Files.readString(Path.of(keyFile)), "EC");
        long now = Instant.now().getEpochSecond();
        String header = base64Url(objectMapper.createObjectNode()
                .put("alg", "ES256")
                .put("kid", keyId)
                .toString().getBytes(StandardCharsets.UTF_8));
        String claims = base64Url(objectMapper.createObjectNode()
                .put("iss", teamId)
                .put("iat", now)
                .toString().getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(key);
        signature.update((header + "." + claims).getBytes(StandardCharsets.UTF_8));
        providerJwt = header + "." + claims + "." + base64Url(signature.sign());
        providerJwtIssuedAt = Instant.now();
        return providerJwt;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
