package org.margin.server.users.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@ToString(exclude = {"encryption", "security"})
@Table(name = "user_security")
public class UserSecurity {
    @Id
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_failed_login_attempt")
    private Instant lastFailedLoginAttempt;

    @Column(name = "account_locked_until")
    private Instant accountLockedUntil;

    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;
}