package org.margin.server.social.messages.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.margin.server.social.conversation.Conversation;
import org.margin.server.users.models.User;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Getter
@Table(name = "messages")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @NotBlank
    @Size(min = 1, max = 5000)
    @Column(nullable = false)
    private String message;

    @Column(name = "is_edited", nullable = false)
    private Boolean isEdited = false;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    public Message(Conversation conversation, User fromUser, String message) {
        this.conversation = conversation;
        this.fromUser = fromUser;
        this.message = message;
        this.isEdited = false;
        this.createdAt = LocalDateTime.now();
    }
}