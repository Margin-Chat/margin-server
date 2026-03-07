package org.margin.server.authentication.models;

public record AuthResponse(
        boolean success,
        String message,
        String token,
        String publicKey,
        String encryptedPrivateKey,
        String salt,
        String iv
) {
}


















