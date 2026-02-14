package org.margin.server.users.models.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeyUploadRequest {
    private String publicKey;
    private String encryptedPrivateKey;
}
