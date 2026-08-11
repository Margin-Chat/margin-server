package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.presence.PresenceService;
import org.margin.server.sfu.services.SfuService;
import org.margin.server.users.api.UserLookup;
import org.margin.server.users.services.UserService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class SfuServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PresenceService presenceService;
    @Mock
    private UserService userService;
    @Mock
    private UserLookup userLookup;

    @InjectMocks
    private SfuService sfuService;

    private MockRestServiceServer sfu;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sfuService, "sfuUrl", "http://sfu.test");
        ReflectionTestUtils.setField(sfuService, "internalApiKey", "secret");
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(sfuService, "restTemplate");
        sfu = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    @DisplayName("participantIdsByChannel should fetch every room in a single call")
    void participantIdsByChannel_FetchesAllRoomsInOneCall() {
        sfu.expect(requestTo("http://sfu.test/rooms/peers"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("X-Internal-Api-Key", "secret"))
                .andRespond(withSuccess("{\"rooms\":{\"1\":[\"7\"],\"3\":[\"8\",\"9\"]}}",
                        MediaType.APPLICATION_JSON));

        Map<Long, List<Long>> result = sfuService.participantIdsByChannel(List.of(1L, 2L, 3L));

        assertEquals(Map.of(1L, List.of(7L), 3L, List.of(8L, 9L)), result);
        sfu.verify();
    }

    @Test
    @DisplayName("participantIdsByChannel should ignore rooms the caller did not ask for")
    void participantIdsByChannel_IgnoresUnrequestedRooms() {
        sfu.expect(requestTo("http://sfu.test/rooms/peers"))
                .andRespond(withSuccess("{\"rooms\":{\"1\":[\"7\"],\"99\":[\"42\"]}}",
                        MediaType.APPLICATION_JSON));

        Map<Long, List<Long>> result = sfuService.participantIdsByChannel(List.of(1L));

        assertEquals(Map.of(1L, List.of(7L)), result);
    }

    @Test
    @DisplayName("participantIdsByChannel should not call the SFU when there are no channels")
    void participantIdsByChannel_SkipsCallForEmptyInput() {
        assertTrue(sfuService.participantIdsByChannel(List.of()).isEmpty());

        sfu.verify();
    }

    @Test
    @DisplayName("participantIdsByChannel should degrade to empty when the SFU is unreachable")
    void participantIdsByChannel_ReturnsEmptyOnFailure() {
        sfu.expect(requestTo("http://sfu.test/rooms/peers")).andRespond(withServerError());

        assertTrue(sfuService.participantIdsByChannel(List.of(1L)).isEmpty());
    }
}
