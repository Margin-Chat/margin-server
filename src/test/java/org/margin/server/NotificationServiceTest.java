package org.margin.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.connection.ConnectionManager;
import org.margin.server.notifications.NotificationService;
import org.margin.server.social.conversation.ConversationType;
import org.margin.server.social.messages.models.dtos.MessageDTO;
import org.margin.server.users.models.User;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.websocket.utils.WebSocketMessageBuilder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private ConnectionManager connectionManager;
    @Mock private WebSocketMessageBuilder messageBuilder;

    @InjectMocks
    private NotificationService notificationService;

    private UserDTO createTestUserDTO() {
        return new UserDTO(
                1L,
                "jdoe",
                "jdoe@example.com",
                "https://cdn.margin.org/pfp/1.png",
                LocalDateTime.now()
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
                Instant.now()
        );
    }

    @Test
    @DisplayName("notifyMessage should only call sendToUser for online recipients")
    void notifyMessage_FiltersByOnlineStatus() {
        // Arrange
        MessageDTO messageDto = createTestMessageDTO();

        User onlineUser = new User();
        onlineUser.setId(1L);

        User offlineUser = new User();
        offlineUser.setId(2L);

        String mockJson = "{\"type\":\"msg\", \"payload\":{...}}";
        when(messageBuilder.message(messageDto)).thenReturn(mockJson);

        when(connectionManager.isUserOnline(1L)).thenReturn(true);
        when(connectionManager.isUserOnline(2L)).thenReturn(false);

        // Act
        notificationService.notifyMessage(messageDto, List.of(onlineUser, offlineUser), ConversationType.DIRECT);

        // Assert
        verify(connectionManager, times(1)).sendToUser(1L, mockJson);
        verify(connectionManager, never()).sendToUser(2L, mockJson);
    }

    @Test
    @DisplayName("notifyUserOffline should broadcast logout event to everyone else")
    void notifyUserOffline_BroadcastsLogout() {
        // Arrange
        User user = new User();
        user.setId(77L);
        String mockJson = "{\"type\":\"USER_LOGOUT\", \"userId\":77}";

        when(messageBuilder.userActivity(any(), eq(user))).thenReturn(mockJson);

        // Act
        notificationService.notifyUserOffline(user);

        // Assert
        verify(connectionManager).broadcast(mockJson, 77L);
    }

    @Test
    @DisplayName("notifyCallCreated should send call details back to the caller")
    void notifyCallCreated_SendsToCaller() {
        // Arrange
        Long callerId = 1L;
        Long callId = 500L;
        String mockJson = "{\"callId\":500}";

        when(messageBuilder.callCreated(callerId, callId)).thenReturn(mockJson);

        // Act
        notificationService.notifyCallCreated(callerId, callId);

        // Assert
        verify(connectionManager).sendToUser(callerId, mockJson);
    }
}