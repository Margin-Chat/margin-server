package org.margin.server.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.presence.PresenceService;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.websocket.services.WebSocketDeliveryService;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private WebSocketDeliveryService webSocketDeliveryService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PresenceService presenceService;

    @Test
    @DisplayName("stampLastSeen sets lastSeenAt to now and saves the user")
    void stampLastSeen_persistsCurrentInstant() {
        User user = createUser(42L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        Instant before = Instant.now();
        presenceService.stampLastSeen(42L);
        Instant after = Instant.now();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        Instant stamped = saved.getValue().getLastSeenAt();
        assertNotNull(stamped);
        assertTrue(!stamped.isBefore(before) && !stamped.isAfter(after),
                "lastSeenAt should be between before/after timestamps");
    }

    @Test
    @DisplayName("stampLastSeen is a no-op when the user does not exist")
    void stampLastSeen_missingUser_doesNotSave() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        presenceService.stampLastSeen(99L);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("stampLastSeen swallows repository exceptions")
    void stampLastSeen_swallowsExceptions() {
        when(userRepository.findById(7L)).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> presenceService.stampLastSeen(7L));
    }

    @Test
    @DisplayName("userDisconnected broadcasts USER_LOGOUT via delivery service")
    void userDisconnected_broadcastsOffline() {
        User user = createUser(3L);

        presenceService.userDisconnected(user);

        verify(webSocketDeliveryService).notifyUserOffline(user);
    }
}