package org.margin.server.social.margin.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.margin.server.social.models.Visibility;
import org.margin.server.social.space.models.Space;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "margins")
@ToString(exclude = {"spaces", "members"})
@EqualsAndHashCode(exclude = {"spaces", "members"})
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
    private List<Space> spaces = new ArrayList<>();

    @OneToMany(mappedBy = "margin", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MarginMember> members = new ArrayList<>();
}
