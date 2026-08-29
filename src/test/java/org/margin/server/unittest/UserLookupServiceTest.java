package org.margin.server.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.services.UserLookupService;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLookupServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserLookupService userLookupService;

    @Test
    @DisplayName("markLastSeen sets lastSeenAt and saves the user")
    void markLastSeen_persistsInstant() {
        User user = createUser(42L);
        Instant lastSeen = Instant.parse("2026-07-28T10:15:30Z");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        userLookupService.markLastSeen(42L, lastSeen);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(lastSeen, saved.getValue().getLastSeenAt());
    }

    @Test
    @DisplayName("markLastSeen is a no-op when the user does not exist")
    void markLastSeen_missingUser_doesNotSave() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        userLookupService.markLastSeen(99L, Instant.now());

        verify(userRepository, never()).save(any());
    }
}
