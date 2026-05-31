package org.margin.server.unittest.utils;

import org.margin.server.users.models.User;
import org.margin.server.users.models.UserEncryption;

public class UserTestUtils {

    public static User createUser(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    public static User createUser(Long id, String handle) {
        User user = new User();
        user.setId(id);
        user.setHandle(handle);
        return user;
    }

    public static User createUser(Long id, String handle, String displayName, String email) {
        User user = new User();
        user.setId(id);
        user.setHandle(handle);
        user.setDisplayName(displayName);
        user.setEmail(email);
        return user;
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
}
