package org.margin.server.users.models.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicKeyResponse {
    private Long userId;
    private String handle;
    private String publicKey;

    public PublicKeyResponse(Long userId, String publicKey) {
        this.userId = userId;
        this.publicKey = publicKey;
    }
}
