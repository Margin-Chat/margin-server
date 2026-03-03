package org.margin.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserEncryption; // Assuming this name
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.repositories.UserRepository;
import org.margin.server.users.services.UserCacheService;
import org.margin.server.users.services.UserService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserCacheService userCacheService;

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
        // Arrange
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        // Assuming User has a getEncryption() that returns a non-null object
        // If it's null by default, you'd need: user.setEncryption(new UserEncryption());
        UserEncryption encryption = new UserEncryption();
        user.setEncryption(encryption);

        when(userCacheService.getById(userId)).thenReturn(user);

        // Act
        userService.savePublicPrivateKeysForUser(userId, "pub-key", "priv-key");

        // Assert
        assertEquals("pub-key", user.getEncryption().getPublicKey());
        assertEquals("priv-key", user.getEncryption().getEncryptedPrivateKey());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("searchForUser should return a list of UserDTOs")
    void searchForUser_ReturnsDtos() {
        // Arrange
        User user = new User();
        user.setId(1L);
        user.setUsername("bob");
        user.setEmail("bob@margin.org");
        user.setCreatedAt(LocalDateTime.now());

        when(userRepository.findTop20ByUsernameContainingIgnoreCase("bo"))
                .thenReturn(List.of(user));

        // Act
        List<UserDTO> result = userService.searchForUser("bo");

        // Assert
        assertEquals(1, result.size());
        assertEquals("bob", result.getFirst().username());
        assertInstanceOf(UserDTO.class, result.getFirst());
    }

    @Test
    @DisplayName("evictUserCache should delegate to UserCacheService")
    void evictUserCache_Delegates() {
        userService.evictUserCache(123L);
        verify(userCacheService).evictUserCache(123L);
    }
}