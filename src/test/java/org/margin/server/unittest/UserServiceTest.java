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
import org.margin.server.websocket.connection.ConnectionManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserCacheService userCacheService;
    @Mock
    private ConnectionManager connectionManager;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("getByUsername should return user when found")
    void getByUsername_Found() {
        User user = new User();
        user.setUsername("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        User result = userService.getByUsername("alice");

        assertEquals("alice", result.getUsername());
    }

    @Test
    @DisplayName("getByUsername should throw exception when not found")
    void getByUsername_NotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.getByUsername("unknown"));
    }

    @Test
    @DisplayName("savePublicPrivateKeysForUser should update encryption details and save")
    void savePublicPrivateKeysForUser_Success() {
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
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
    void searchUsers_ReturnsGroupedResults() {
        User user = new User();
        user.setId(2L);
        user.setUsername("bob");
        user.setEmail("bob@margin.org");
        user.setCreatedAt(LocalDateTime.now());

        Long searcherId = 1L;

        when(userRepository.findUsersInSharedMargins(searcherId, "bo"))
                .thenReturn(List.of(
                        new UserWithSharedMarginProjection(user, "Team Alpha"),
                        new UserWithSharedMarginProjection(user, "Team Beta")
                ));
        when(connectionManager.isUserOnline(2L)).thenReturn(true);

        List<UserSearchResultDTO> result = userService.searchUsers(searcherId, "bo");

        assertEquals(1, result.size());
        assertEquals("bob", result.getFirst().user().username());
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