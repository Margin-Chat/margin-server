package org.margin.server.authentication.models;

public record AuthResponse(
        boolean success,
        String message,
        String token,
        String refreshToken,
        String publicKey,
        String encryptedPrivateKey,
        String salt,
        String iv
) {
    public static AuthResponse failure(String message) {
        return new AuthResponse(false, message, null, null, null, null, null, null);
    }
}


















