package org.margin.server.authentication.models;

public record RegisterRequest(
        String handle,
        String displayName,
        String email,
        String password,
        String publicKey,
        String encryptedPrivateKey,
        String salt,
        String iv,
        String betaKey
) {
}