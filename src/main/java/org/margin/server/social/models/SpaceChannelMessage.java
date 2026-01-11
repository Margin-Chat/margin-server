package org.margin.server.social.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "channel_messages")
public class SpaceChannelMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "channel_message_id")
    private Long id;

    @NotNull
    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @NotNull
    @Column(name = "to_channel_id", nullable = false)
    private Long channelId;

    @NotNull
    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "from_user_server")
    private String fromUserServer;

    @NotBlank
    @Size(min = 1, max = 5000)
    private String message;

    private Boolean isEdited = false;
    private Date createdAt;

    public SpaceChannelMessage(Long fromUserId,
                               Long channelId,
                               Long spaceId,
                               String message) {
        this.fromUserId = fromUserId;
        this.channelId = channelId;
        this.spaceId = spaceId;
        this.message = message;
        this.createdAt = new Date();
    }
}
