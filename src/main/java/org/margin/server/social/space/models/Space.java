package org.margin.server.social.space.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import org.margin.server.social.channel.entities.Channel;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.social.models.Visibility;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@ToString(exclude = {"margin", "channels", "members"})
@Table(name = "spaces")
@SQLRestriction("deleted_at IS NULL")
public class Space {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "space_id")
    private Long id;

    @NotNull
    @Column(nullable = false, name = "name")
    private String name;

    @NotNull
    @Column(nullable = false, name = "description")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "visibility")
    private Visibility visibility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "margin_id", nullable = false)
    private Margin margin;

    @OneToMany(mappedBy = "space", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Channel> channels = new ArrayList<>();

    @OneToMany(mappedBy = "space", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SpaceMember> members = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
