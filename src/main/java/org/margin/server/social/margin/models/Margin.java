package org.margin.server.social.margin.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.margin.server.social.models.Visibility;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "margins")
public class Margin {
    @Id
    @Column(name = "margin_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "name")
    private String name;

    @NotNull
    @Column(name = "description")
    private String description;

    @NotNull
    @Column(name = "visibility")
    private Visibility visibility;

    @Column(name = "icon_url", length = 500)
    private String marginIconUrl;
}
