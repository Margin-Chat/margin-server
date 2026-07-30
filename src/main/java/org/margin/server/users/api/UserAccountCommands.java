package org.margin.server.users.api;

public interface UserAccountCommands {

    boolean emailIsTaken(String email);

    Long register(NewUser newUser);

    void resetCredentials(Long userId, String encodedPassword);

    void updateEncryptionKeys(Long userId, String publicKey, String encryptedPrivateKey, String salt, String iv);

    void invalidateCachedUser(Long userId);

    record NewUser(
            String displayName,
            String email,
            String encodedPassword,
            String profilePictureUrl,
            String publicKey,
            String encryptedPrivateKey,
            String salt,
            String iv
    ) {
    }
}
