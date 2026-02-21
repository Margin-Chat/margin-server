package org.margin.server.social.margin.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.space.models.Space;
import org.margin.server.users.models.Role;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "margins")
public class Margin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "margin_id")
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

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @OneToMany(mappedBy = "margin", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Space> spaces;

    @OneToMany(mappedBy = "margin", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MarginMember> members;
}
