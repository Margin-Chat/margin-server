package org.margin.server.users.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long id;

	@NotBlank
	@Size(min = 3, max = 50)
	@Column(unique = true, nullable = false)
	private String username;

	@NotBlank
	@Email
	@Column(nullable = false)
	private String email;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@NotBlank
	@Column(nullable = false)
	private String password;

    @Column(nullable = false)
    private Role role = Role.USER;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

    @Column(length = 2048)
    private String publicKey;

    @Column(length = 4096)
    private String encryptedPrivateKey;

    @Column(length = 512)
    private String salt;

    @Column(length = 512)
    private String iv;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_failed_login_attempt")
    private LocalDateTime lastFailedLoginAttempt;

    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
	}
}
