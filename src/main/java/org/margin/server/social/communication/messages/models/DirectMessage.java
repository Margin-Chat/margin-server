package org.margin.server.social.communication.messages.models;

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
@Table(name = "direct_messages")
public class DirectMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "direct_message_id")
    private Long id;

    @NotNull
    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @NotNull
    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(name = "from_user_server")
    private String fromUserServer;

    @Column(name = "to_user_server")
    private String toUserServer;

    @NotBlank
    @Size(min = 1, max = 5000)
    private String message;

    private Boolean isRead = false;
    private Boolean isEdited = false;
    private Date createdAt;

    public DirectMessage(Long fromUserId,
                         Long toUserId,
                         String fromUserServer,
                         String toUserServer,
                         String message) {
        this.fromUserId = fromUserId;
        this.fromUserServer = fromUserServer;
        this.toUserId = toUserId;
        this.toUserServer = toUserServer;
        this.message = message;
        this.isRead = false;
        this.isEdited = false;
        this.createdAt = new Date();
    }
}