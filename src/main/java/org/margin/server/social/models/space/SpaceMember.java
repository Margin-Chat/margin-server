package org.margin.server.social.models.space;

import jakarta.persistence.*;
import org.margin.server.social.models.space.enums.SpaceMemberRole;
import org.margin.server.users.models.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "space_members")
public class SpaceMember {
    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Enumerated(EnumType.STRING)
    private SpaceMemberRole role;
}
