package org.margin.server.unittest.utils;

import org.margin.server.shared.security.AuthenticatedUser;
import org.margin.server.users.models.User;
import org.margin.server.users.models.UserEncryption;

public class UserTestUtils {

    public static User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    public static User createUser(Long id, String displayName) {
        User user = new User();
        user.setId(id);
        user.setDisplayName(displayName);
        return user;
    }

    public static User createUser(Long id, String displayName, String email) {
        User user = new User();
        user.setId(id);
        user.setDisplayName(displayName);
        user.setEmail(email);
        return user;
    }

    public static AuthenticatedUser authUser(Long id) {
        return new AuthenticatedUser(id, null, null);
    }

    public static AuthenticatedUser authUser(Long id, String displayName) {
        return new AuthenticatedUser(id, null, displayName);
    }

    public static UserEncryption createEncryption(String publicKey, String encryptedPrivateKey,
                                                  String salt, String iv) {
        UserEncryption encryption = new UserEncryption();
        encryption.setPublicKey(publicKey);
        encryption.setEncryptedPrivateKey(encryptedPrivateKey);
        encryption.setSalt(salt);
        encryption.setIv(iv);
        return encryption;
    }

    public static AuthenticatedUser principalOf(User user) {
        return user == null ? null
                : new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
