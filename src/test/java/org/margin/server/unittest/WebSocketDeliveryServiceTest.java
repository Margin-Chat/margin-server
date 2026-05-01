package org.margin.server.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.social.conversation.models.ConversationType;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.social.space.repositories.SpaceMemberRepository;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.services.UserService;
import org.margin.server.websocket.connection.ConnectionManager;
import org.margin.server.websocket.models.WebSocketMessageType;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketDeliveryServiceTest {

    @Mock
    private ConnectionManager connectionManager;
    @Mock
    private WebSocketMessageBuilder messageBuilder;
    @Mock
    private SpaceMemberRepository spaceMemberRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private WebSocketDeliveryService webSocketDeliveryService;

    private UserDTO createTestUserDTO() {
        return new UserDTO(
                1L,
                "jdoe",
                "jdoe",
                "jdoe@example.com",
                "https://cdn.margin.org/pfp/1.png",
                Instant.now(),
                true
        );
    }

    private MessageDTO createTestMessageDTO() {
        return new MessageDTO(
                100L,
                10L,
                ConversationType.DIRECT,
                createTestUserDTO(),
                "Testing message notification",
                false,
                Instant.now(),
                1L,
                "Test",
                List.of(),
                null
        );
    }

    @Test
    @DisplayName("notifyMessage should only call sendToUser for online recipients")
    void notifyMessage_FiltersByOnlineStatus() {
        MessageDTO messageDto = createTestMessageDTO();

        User onlineUser = new User();
        onlineUser.setId(1L);

        User offlineUser = new User();
        offlineUser.setId(2L);

        String mockJson = "{\"type\":\"msg\", \"payload\":{...}}";

        when(messageBuilder.buildMessage(eq(WebSocketMessageType.RECEIVE_MESSAGE), eq(messageDto.conversationId()), any()))
                .thenReturn(mockJson);

        when(connectionManager.isUserOnline(1L)).thenReturn(true);
        when(connectionManager.isUserOnline(2L)).thenReturn(false);

        webSocketDeliveryService.notifyMessage(messageDto, List.of(onlineUser, offlineUser), ConversationType.DIRECT);

        verify(connectionManager, times(1)).sendToUser(1L, mockJson);
        verify(connectionManager, never()).sendToUser(eq(2L), any());
    }

    @Test
    @DisplayName("notifyUserOffline should broadcast logout event to everyone else")
    void notifyUserOffline_BroadcastsLogout() {
        User user = new User();
        user.setId(77L);
        String mockJson = "{\"type\":\"USER_LOGOUT\", \"userId\":77}";

        when(messageBuilder.buildMessage(eq(WebSocketMessageType.USER_LOGOUT), eq(77L), any()))
                .thenReturn(mockJson);

        webSocketDeliveryService.notifyUserOffline(user);

        verify(connectionManager).broadcast(mockJson, 77L);
    }

    @Test
    @DisplayName("notifyCallCreated should send call details back to the caller")
    void notifyCallCreated_SendsToCaller() {
        Long callerId = 1L;
        Long callId = 500L;
        String mockJson = "{\"callId\":500}";

        when(messageBuilder.buildMessage(eq(WebSocketMessageType.CALL_CREATED), eq(callerId), any()))
                .thenReturn(mockJson);

        webSocketDeliveryService.notifyCallCreated(callerId, callId);

        verify(connectionManager).sendToUser(callerId, mockJson);
    }
}