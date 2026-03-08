package org.margin.server.social.channel.channel;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.space.models.Space;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "channels")
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
}