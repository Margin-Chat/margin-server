package org.margin.server.users.api;

public interface UserAccountCommands {

    boolean emailIsTaken(String email);

    Long register(NewUser newUser);

    void resetCredentials(Long userId, String encodedPassword);

    void updateEncryptionKeys(Long userId, String publicKey, String encryptedPrivateKey, String salt, String iv);

    void invalidateCachedUser(Long userId);

    /**
     * Creates a throwaway account for someone joining a meeting by link. The row exists so every
     * Long-keyed path (SFU peer ids, presence, notifications) keeps working; it carries a
     * synthetic unresolvable email and an unknowable password, and cannot be logged into.
     */
    Long createGuest(String displayName, java.time.Instant expiresAt);

    /** Converts a guest row into a real account, keeping the same userId so history survives. */
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
