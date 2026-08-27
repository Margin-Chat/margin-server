package org.margin.server.meetings.models.dtos;

public record ClaimGuestRequest(
        String email,
        String password,
        String publicKey,
        String encryptedPrivateKey,
        String salt,
        String iv
) {
}
