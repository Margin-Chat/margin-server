package org.margin.server.bugs.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.margin.server.users.models.User;

@Entity
@Table(name = "bug_reports")
@Getter
@Setter
public class BugReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;
}
