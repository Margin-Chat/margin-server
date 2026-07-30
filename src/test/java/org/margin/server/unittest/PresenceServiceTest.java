package org.margin.server.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.presence.PresenceService;
import org.margin.server.presence.events.UserDisconnectedEvent;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PresenceService presenceService;

    @Test
    @DisplayName("userDisconnected publishes UserDisconnectedEvent")
    void userDisconnected_publishesEvent() {
        presenceService.userDisconnected(3L);

        ArgumentCaptor<UserDisconnectedEvent> captor = ArgumentCaptor.forClass(UserDisconnectedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertNotNull(captor.getValue());
    }
}
