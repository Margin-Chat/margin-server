package org.margin.server.social.conversation.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.margin.server.users.models.User;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "conversation_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"conversation_id", "user_id"})
})
public class ConversationMember {

    @EmbeddedId
    private ConversationMemberId id = new ConversationMemberId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("conversationId")
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @NotNull
    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "invite_status", nullable = false, length = 10)
    private ConversationInviteStatus inviteStatus = ConversationInviteStatus.ACCEPTED;

    @Column(name = "invited_at")
    private Instant invitedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant acceptedAt;
}