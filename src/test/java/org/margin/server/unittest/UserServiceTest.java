package org.margin.server.unittest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserEncryption;
import org.margin.server.users.models.dtos.UserSearchResultDTO;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.repositories.projections.UserWithSharedMarginProjection;
import org.margin.server.users.services.UserCacheService;
import org.margin.server.users.services.UserService;
import org.margin.server.presence.PresenceService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.margin.server.unittest.utils.UserTestUtils.createUser;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserCacheService userCacheService;
    @Mock
    private PresenceService presenceService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("getByEmail should return user when found")
    void getByEmail_Found() {
        User user = createUser(null, "Alice", "alice@margin.org");
        when(userRepository.findByEmail("alice@margin.org")).thenReturn(Optional.of(user));

        User result = userService.getByEmail("alice@margin.org");

        assertEquals("alice@margin.org", result.getEmail());
    }

    @Test
    @DisplayName("getByEmail should throw exception when not found")
    void getByEmail_NotFound() {
        when(userRepository.findByEmail("unknown@margin.org")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.getByEmail("unknown@margin.org"));
    }

    @Test
    @DisplayName("savePublicPrivateKeysForUser should update encryption details and save")
    void savePublicPrivateKeysForUser_Success() {
        Long userId = 1L;
        User user = createUser(userId);
        UserEncryption encryption = new UserEncryption();
        user.setEncryption(encryption);

        when(userCacheService.getById(userId)).thenReturn(user);

        userService.savePublicPrivateKeysForUser(userId, "pub-key", "priv-key");

        assertEquals("pub-key", user.getEncryption().getPublicKey());
        assertEquals("priv-key", user.getEncryption().getEncryptedPrivateKey());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("searchUsers should return grouped results with shared margins")
    void searchUsers_WithSharedMargins_ReturnsGroupedResults() {
        User user = createUser(2L, "bob", "bob@margin.org");
        user.setCreatedAt(Instant.now());

        Long searcherId = 1L;

        when(userRepository.findUsersWithSharedMargins(searcherId, "bo"))
                .thenReturn(List.of(
                        new UserWithSharedMarginProjection(user, "Team Alpha"),
                        new UserWithSharedMarginProjection(user, "Team Beta")
                ));
        when(presenceService.isUserOnline(2L)).thenReturn(true);

        List<UserSearchResultDTO> result = userService.searchUsersWithSharedMargins(searcherId, "bo");

        assertEquals(1, result.size());
        assertEquals("bob", result.getFirst().user().displayName());
        assertTrue(result.getFirst().user().isOnline());
        assertEquals(List.of("Team Alpha", "Team Beta"), result.getFirst().sharedMargins());
    }

    @Test
    @DisplayName("evictUserCache should delegate to UserCacheService")
    void evictUserCache_Delegates() {
        userService.evictUserCache(123L);
        verify(userCacheService).evictUserCache(123L);
    }
}