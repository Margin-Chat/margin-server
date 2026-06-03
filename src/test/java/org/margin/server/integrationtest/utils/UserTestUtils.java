package org.margin.server.integrationtest.utils;

import org.margin.server.users.controllers.UserController;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserEncryption;
import org.margin.server.users.models.UserSecurity;
import org.margin.server.users.models.dtos.CurrentUserDTO;
import org.margin.server.users.models.dtos.KeyUploadRequest;
import org.margin.server.users.models.dtos.UserDTO;
import org.margin.server.users.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class UserTestUtils {
    private static UserRepository userRepository;
    private static UserController userController;

    @Autowired
    public UserTestUtils(UserRepository userRepository,
                         UserController userController) {
        UserTestUtils.userRepository = userRepository;
        UserTestUtils.userController = userController;
    }

    public static User createUser(String displayName, String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPassword("hashed-password");
        user.setCreatedAt(Instant.now());

        UserEncryption encryption = new UserEncryption();
        encryption.setUser(user);
        user.setEncryption(encryption);

        UserSecurity security = new UserSecurity();
        security.setUser(user);
        user.setSecurity(security);

        return userRepository.save(user);
    }

    public static CurrentUserDTO getCurrentUser(User user) {
        return userController.getCurrentUser(user);
    }

    public static CurrentUserDTO updateUser(String displayName, String email, User user) {
        return userController.updateUserInfo(displayName, email, null, user);
    }

    public static ResponseEntity<UserDTO> lookupByEmail(User requester, String email) {
        return userController.lookupByEmail(requester, email);
    }

    public static ResponseEntity<Void> uploadKeys(Long userId, String publicKey, String encryptedPrivateKey, User user) {
        return userController.uploadKeys(userId, new KeyUploadRequest(publicKey, encryptedPrivateKey), user);
    }

    public static void deleteUser(User user) {
        userController.deleteUser(user);
    }

    public static User findById(Long userId) {
        return userRepository.findById(userId).orElseThrow();
    }
}
