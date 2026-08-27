package org.margin.server.users.api;

public interface UserAccountCommands {
    boolean emailIsTaken(String email);

    Long register(NewUser newUser);

    void resetCredentials(Long userId, String encodedPassword);

    void updateEncryptionKeys(Long userId, String publicKey, String encryptedPrivateKey, String salt, String iv);

    void invalidateCachedUser(Long userId);

    Long createGuest(String displayName, java.time.Instant expiresAt);

    void promoteGuest(Long userId, String email, String encodedPassword, String publicKey,
                      String encryptedPrivateKey, String salt, String iv);

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
