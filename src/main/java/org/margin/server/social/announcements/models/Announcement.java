package org.margin.server.social.announcements.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.margin.server.social.margin.entities.Margin;
import org.margin.server.users.models.User;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "announcements")
public class Announcement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long announcementId;

    @ToString.Exclude
    @ManyToOne
    @JoinColumn(nullable = false, name = "margin_id")
    private Margin margin;

    @ManyToOne
    @JoinColumn(nullable = false, name = "author_id")
    private User author;

    @NotNull
    @Column(nullable = false, name = "title")
    private String title;

    @NotNull
    @Column(nullable = false, name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;
}