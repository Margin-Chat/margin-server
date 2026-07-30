package org.margin.server.social.margin.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "margin_invites")
public class MarginInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "margin_id", nullable = false)
    private Margin margin;

    @Column(name = "invited_by_user_id", nullable = false)
    private Long invitedByUserId;

    @Column(name = "invited_user_id")
    private Long invitedUserId;

    @Column(name = "invite_code", nullable = false, unique = true)
    private String inviteCode = UUID.randomUUID().toString();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InviteStatus status = InviteStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InviteType type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "current_uses", nullable = false)
    private int currentUses = 0;

    public enum InviteStatus {
        PENDING, ACCEPTED, DECLINED, EXPIRED
    }

    public enum InviteType {
        LINK, DIRECT
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return status == InviteStatus.PENDING
                && !isExpired()
                && (maxUses == null || currentUses < maxUses);
    }
}