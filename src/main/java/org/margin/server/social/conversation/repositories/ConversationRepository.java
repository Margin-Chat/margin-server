package org.margin.server.social.conversation.repositories;

import org.margin.server.social.conversation.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @Query("""
        SELECT c FROM Conversation c
        WHERE c.type = 'DIRECT'
        AND c.id IN (
            SELECT cm1.conversation.id FROM ConversationMember cm1
            WHERE cm1.user.id = :userId1
        )
        AND c.id IN (
            SELECT cm2.conversation.id FROM ConversationMember cm2
            WHERE cm2.user.id = :userId2
        )
        AND (
            SELECT COUNT(cm) FROM ConversationMember cm
            WHERE cm.conversation.id = c.id
        ) = 2
    """)
    Optional<Conversation> findDirectConversationBetweenUsers(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );

    @Query("""
        SELECT DISTINCT c FROM Conversation c
        JOIN ConversationMember cm ON cm.conversation.id = c.id
        WHERE cm.user.id = :userId
        ORDER BY c.createdAt DESC
    """)
    List<Conversation> findByUserId(@Param("userId") Long userId);

    Optional<Conversation> findByChannelId(Long channelId);

    @Query("SELECT c FROM Conversation c " +
            "LEFT JOIN FETCH c.channel " +
            "WHERE c.id = :id")
    Optional<Conversation> findByIdWithAssociations(@Param("id") Long id);}
