package org.margin.server.social.margin.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.margin.server.social.margin.models.MarginRole;

import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "margin_members")
@ToString(exclude = {"margin"})
@EqualsAndHashCode(exclude = {"margin"})
public class MarginMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "margin_member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "margin_id", nullable = false)
    private Margin margin;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "role")
    private MarginRole role;

    @Column(name = "joined_at")
    private Instant joinedAt;
}