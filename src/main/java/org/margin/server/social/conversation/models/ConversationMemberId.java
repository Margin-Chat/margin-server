package org.margin.server.social.conversation.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ConversationMemberId implements Serializable {
    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "user_id")
    private Long userId;
}