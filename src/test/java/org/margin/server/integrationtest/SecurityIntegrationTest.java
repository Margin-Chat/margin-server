package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.authentication.services.JwtService;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.integrationtest.utils.AuthTestUtils;
import org.margin.server.users.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SecurityIntegrationTest extends MarginTestRunner {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Autowired
    private JwtService jwtService;

    private String base() {
        return "http://localhost:" + port;
    }

    @Test
    void unauthenticatedRequestToProtectedEndpoint_returns401() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base() +"/api/users/me")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(401, response.statusCode());
    }

    @Test
    void requestWithLiteralBearerNull_returns401() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base() +"/api/users/me"))
                        .header("Authorization", "Bearer null")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(401, response.statusCode());
    }

    @Test
    void requestWithMalformedBearer_returns401() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base() +"/api/users/me"))
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(401, response.statusCode());
    }

    @Test
    void publicEndpointDoesNotRequireAuth() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base() +"/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"x\",\"password\":\"y\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertNotEquals(401, response.statusCode(), "/api/auth/login must not require auth");
        assertNotEquals(403, response.statusCode(), "/api/auth/login must not require auth");
    }

    @Test
    void getNonexistentChannel_returns404NotInternalServerError() throws Exception {
        User user = UserTestUtils.createUser("alice_sec", "alice_sec@margin.chat");
        String token = jwtService.generateToken(user.getEmail(), user.getId(), AuthTestUtils.securityOf(user).getTokenVersion());

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base() +"/api/channels/9999999"))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(404, response.statusCode(),
                "Missing channel must produce a 404, not a 500. Body: " + response.body());
        assertTrue(response.body().contains("Channel not found"),
                "Expected ProblemDetail to mention 'Channel not found', got: " + response.body());
    }

    @Test
    void tokenIsRejectedAfterLogout() throws Exception {
        User user = UserTestUtils.createUser("logout_sec", "logout_sec@margin.chat");
        String token = jwtService.generateToken(user.getEmail(), user.getId(), AuthTestUtils.securityOf(user).getTokenVersion());

        assertEquals(200, get("/api/users/me", token).statusCode(),
                "Token must be accepted before logout");

        HttpResponse<String> logout = client.send(
                HttpRequest.newBuilder(URI.create(base() + "/api/auth/logout"))
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, logout.statusCode());

        assertEquals(401, get("/api/users/me", token).statusCode(),
                "The same token must be revoked (401) after logout bumps the token version");
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(base() + path))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }
}
