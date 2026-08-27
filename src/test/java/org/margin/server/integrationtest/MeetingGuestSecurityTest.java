package org.margin.server.integrationtest;

import org.junit.jupiter.api.Test;
import org.margin.server.integrationtest.config.MarginTestRunner;
import org.margin.server.integrationtest.utils.MarginTestUtils;
import org.margin.server.integrationtest.utils.SubscriptionTestUtils;
import org.margin.server.integrationtest.utils.UserTestUtils;
import org.margin.server.meetings.models.dtos.CreateMeetingRequest;
import org.margin.server.meetings.models.dtos.GuestSessionResponse;
import org.margin.server.meetings.models.dtos.MeetingDTO;
import org.margin.server.meetings.services.MeetingGuestService;
import org.margin.server.meetings.services.MeetingService;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.subscriptions.models.SubscriptionTier;
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
class MeetingGuestSecurityTest extends MarginTestRunner {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Autowired
    private MeetingService meetingService;
    @Autowired
    private MeetingGuestService guestService;

    private String base() {
        return "http://localhost:" + port;
    }

    private MeetingDTO newMeeting() {
        User host = UserTestUtils.createUser("alice", "alice@margin.chat");
        Margin margin = MarginTestUtils.createMargin("Acme", host);
        SubscriptionTestUtils.setActiveSubscription(margin, "c", "s", SubscriptionTier.SMALL);
        return meetingService.createInstantMeeting(host.getId(),
                new CreateMeetingRequest(margin.getId(), "Standup", null));
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base() + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String guestToken() {
        MeetingDTO meeting = newMeeting();
        GuestSessionResponse session = guestService.createGuestSession(meeting.code(), "Wanderer");
        return session.guestToken();
    }

    @Test
    void previewIsReachableWithoutAnyCredential() throws Exception {
        MeetingDTO meeting = newMeeting();

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(base() + "/api/meetings/" + meeting.code() + "/preview"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
    }

    @Test
    void otherMeetingEndpointsStillRequireACredential() throws Exception {
        MeetingDTO meeting = newMeeting();

        assertEquals(401, get("/api/meetings/" + meeting.code(), null).statusCode());
    }

    @Test
    void aGuestTokenCannotReachTheRestOfTheApi() throws Exception {
        String token = guestToken();

        assertEquals(401, get("/api/users/me", token).statusCode(),
                "the guest credential must be confined to /api/meetings/**");
        assertEquals(401, get("/api/margins/get_margins", token).statusCode());
        assertEquals(401, get("/api/notifications/all", token).statusCode());
    }

    @Test
    void aGuestTokenIsAcceptedOnTheMeetingsChain() throws Exception {
        String token = guestToken();

        int status = get("/api/meetings/eligible-margins", token).statusCode();

        assertNotEquals(401, status,
                "positive control: the guest token authenticates on its own chain");
    }
}
