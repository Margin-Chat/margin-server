package org.margin.server.social.channel.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.margin.server.social.channel.models.ChannelType;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.space.models.Space;

import java.time.Instant;


@NoArgsConstructor
@AllArgsConstructor
@Entity
@Getter
@Setter
@Table(name = "channels")
@SQLRestriction("deleted_at IS NULL")
public class Channel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "channel_id")
    private Long id;

    @NotNull
    @Column(nullable = false, name = "name")
    private String name;

    @NotNull
    @Column(nullable = false, name = "description")
    private String description;

    @NotNull
    @Column(nullable = false, name = "channel_type")
    private ChannelType channelType;

    @ToString.Exclude
    @ManyToOne
    @JoinColumn(nullable = false, name = "space_id")
    private Space space;

    @ToString.Exclude
    @OneToOne(mappedBy = "channel", cascade = CascadeType.ALL)
    private Conversation conversation;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}