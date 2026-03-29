package org.margin.server.authentication.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.margin.server.users.models.User;

import java.time.Instant;

@Entity
@Table(name = "beta_keys")
@Getter
@Setter
@NoArgsConstructor
public class BetaKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String key;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by")
    private User usedBy;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean isUsed() {
        return usedBy != null;
    }
}