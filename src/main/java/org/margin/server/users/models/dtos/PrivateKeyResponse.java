package org.margin.server.users.models.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrivateKeyResponse {
    private Long userId;
    private String encryptedPrivateKey;
    private String salt;
    private String iv;

    public PrivateKeyResponse(String encryptedPrivateKey, String salt, String iv) {
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.salt = salt;
        this.iv = iv;
    }
}
