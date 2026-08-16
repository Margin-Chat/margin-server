package org.margin.server.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.sfu.SfuController;
import org.margin.server.sfu.services.SfuService;
import org.margin.server.sfu.services.SfuTokenService;
import org.margin.server.shared.authorization.MarginAccessChecker;
import org.margin.server.social.api.ChannelLookup;
import org.margin.server.subscriptions.services.SubscriptionValidationService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.margin.server.unittest.utils.ControllerTestSupport.standaloneMockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The peer_joined/peer_left callbacks are permitAll and were parsing roomId as a channel id, so a
 * meeting room id used to blow up with NumberFormatException on every peer event.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SfuControllerPeerCallbackTest {

    @Mock
    private SfuService sfuService;
    @Mock
    private MarginAccessChecker marginAccessChecker;
    @Mock
    private SfuTokenService sfuTokenService;
    @Mock
    private ChannelLookup channelLookup;
    @Mock
    private SubscriptionValidationService subscriptionValidationService;

    @InjectMocks
    private SfuController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneMockMvc(controller);
    }

    private void postCallback(String path, String roomId, String peerId) throws Exception {
        mockMvc.perform(post("/api/sfu/" + path)
                        .header("X-Internal-Api-Key", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"" + roomId + "\",\"peerId\":\"" + peerId + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a channel room still routes to the channel voice notification, unchanged")
    void peerJoined_ChannelRoom_NotifiesChannel() throws Exception {
        postCallback("peer_joined", "42", "7");

        verify(sfuService).notifyUserJoined(42L, 7L);
        verify(sfuService, never()).notifyMeetingPeerJoined(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("a channel room leaving still routes to the channel voice notification")
    void peerLeft_ChannelRoom_NotifiesChannel() throws Exception {
        postCallback("peer_left", "42", "7");

        verify(sfuService).notifyUserLeft(42L, 7L);
    }

    @Test
    @DisplayName("a meeting room joining publishes a meeting event instead of 500ing")
    void peerJoined_MeetingRoom_NotifiesMeeting() throws Exception {
        postCallback("peer_joined", "m_abc", "7");

        verify(sfuService).notifyMeetingPeerJoined("abc", "7");
        verify(sfuService, never()).notifyUserJoined(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("a meeting room leaving publishes a meeting event instead of 500ing")
    void peerLeft_MeetingRoom_NotifiesMeeting() throws Exception {
        postCallback("peer_left", "m_abc", "7");

        verify(sfuService).notifyMeetingPeerLeft("abc", "7");
    }

    @Test
    @DisplayName("an unrecognised room id is a bad request, not a server error")
    void peerJoined_GarbageRoomId_IsBadRequest() throws Exception {
        mockMvc.perform(post("/api/sfu/peer_joined")
                        .header("X-Internal-Api-Key", "secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"banana\",\"peerId\":\"7\"}"))
                .andExpect(status().isBadRequest());
    }
}
