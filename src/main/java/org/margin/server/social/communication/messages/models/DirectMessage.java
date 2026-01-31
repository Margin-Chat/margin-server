package org.margin.server.social.communication.messages.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.margin.server.users.models.User;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "direct_messages")
public class DirectMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "direct_message_id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    @NotBlank
    @Size(min = 1, max = 5000)
    private String message;

    private Boolean isRead = false;
    private Boolean isEdited = false;
    private Date createdAt;

    public DirectMessage(User fromUser,
                         User toUser,
                         String message) {
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.message = message;
        this.isRead = false;
        this.isEdited = false;
        this.createdAt = new Date();
    }
}