package org.margin.server.authentication.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@ToString
@Table(name = "user_security")
public class UserSecurity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_failed_login_attempt")
    private Instant lastFailedLoginAttempt;

    @Column(name = "account_locked_until")
    private Instant accountLockedUntil;

    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    public UserSecurity(Long userId) {
        this.userId = userId;
    }
}
