package org.margin.server.users.models.dtos;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword,
        String encryptedPrivateKey,
        String salt,
        String iv
) {
}
