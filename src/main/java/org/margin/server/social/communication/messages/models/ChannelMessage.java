package org.margin.server.social.communication.messages.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.margin.server.social.models.channel.Channel;
import org.margin.server.users.models.User;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "channel_messages")
public class ChannelMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "channel_message_id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @NotNull
    @JoinColumn(name = "to_channel_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Channel channel;

    @NotBlank
    @Size(min = 1, max = 5000)
    private String message;

    private Boolean isEdited = false;
    private Date createdAt;

    public ChannelMessage(User fromUser,
                          Channel channel,
                          String message) {
        this.fromUser = fromUser;
        this.channel = channel;
        this.message = message;
        this.createdAt = new Date();
    }
}
