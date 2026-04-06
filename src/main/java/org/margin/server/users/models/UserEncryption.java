package org.margin.server.users.models;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@ToString(exclude = {"encryption", "security"})
@Table(name = "user_encryption")
public class UserEncryption {
    @Id
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 2048)
    private String publicKey;

    @Column(length = 4096)
    private String encryptedPrivateKey;

    @Column(length = 512)
    private String salt;

    @Column(length = 512)
    private String iv;
}