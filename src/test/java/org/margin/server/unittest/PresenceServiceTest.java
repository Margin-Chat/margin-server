package org.margin.server.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.presence.PresenceService;
import org.margin.server.presence.events.UserDisconnectedEvent;
import org.margin.server.users.models.User;
import org.margin.server.users.api.UserLookup;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserLookup userLookup;

    @InjectMocks
    private PresenceService presenceService;

    @Test
    @DisplayName("stampLastSeen marks the user as last seen now")
    void stampLastSeen_marksCurrentInstant() {
        Instant before = Instant.now();
        presenceService.stampLastSeen(42L);
        Instant after = Instant.now();

        ArgumentCaptor<Instant> stamped = ArgumentCaptor.forClass(Instant.class);
        verify(userLookup).markLastSeen(eq(42L), stamped.capture());
        assertNotNull(stamped.getValue());
        assertTrue(!stamped.getValue().isBefore(before) && !stamped.getValue().isAfter(after),
                "lastSeenAt should be between before/after timestamps");
    }

    @Test
    @DisplayName("stampLastSeen swallows lookup exceptions")
    void stampLastSeen_swallowsExceptions() {
        doThrow(new RuntimeException("db down")).when(userLookup).markLastSeen(eq(7L), any());

        assertDoesNotThrow(() -> presenceService.stampLastSeen(7L));
    }

    @Test
    @DisplayName("userDisconnected publishes UserDisconnectedEvent")
    void userDisconnected_publishesEvent() {
        User user = createUser(3L);

        presenceService.userDisconnected(user);

        ArgumentCaptor<UserDisconnectedEvent> captor = ArgumentCaptor.forClass(UserDisconnectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertNotNull(captor.getValue());
    }
}
