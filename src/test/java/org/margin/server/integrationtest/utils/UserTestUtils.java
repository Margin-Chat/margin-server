package org.margin.server.integrationtest.utils;

import org.margin.server.users.controllers.UserController;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserEncryption;
import org.margin.server.users.models.UserSecurity;
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

    public static User createUser(String handle, String email) {
        User user = new User();
        user.setHandle(handle);
        user.setEmail(email);
        user.setDisplayName(handle);
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

    public static UserDTO getCurrentUser(User user) {
        return userController.getCurrentUser(user);
    }

    public static UserDTO updateUser(String displayName, String email, User user) {
        return userController.getCurrentUser(displayName, email, null, user);
    }

    public static ResponseEntity<UserDTO> lookupByHandle(String handle) {
        return userController.lookupByHandle(handle);
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
