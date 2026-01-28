package org.margin.server.authentication.models;

public record RegisterRequest(
        String username,
        String email,
        String password,
        String publicKey,
        String encryptedPrivateKey,
        String salt,
        String iv
) {
}