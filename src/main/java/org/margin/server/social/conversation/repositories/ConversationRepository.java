package org.margin.server.social.conversation.repositories;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.projections.ThreadSummaryProjection;
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
                    WHERE cm1.id.userId = :userId1
                )
                AND c.id IN (
                    SELECT cm2.conversation.id FROM ConversationMember cm2
                    WHERE cm2.id.userId = :userId2
                )
                AND (
                    SELECT COUNT(cm) FROM ConversationMember cm
                    WHERE cm.conversation.id = c.id
                ) = 2
                ORDER BY c.id ASC
            """)
    List<Conversation> findDirectConversationBetweenUsers(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );

    @Query("""
                SELECT DISTINCT c FROM Conversation c
                JOIN ConversationMember cm ON cm.conversation.id = c.id
                WHERE cm.id.userId = :userId
                AND c.type <> 'THREAD'
                ORDER BY c.createdAt DESC
            """)
    List<Conversation> findByUserId(@Param("userId") Long userId);

    @Query("""
                SELECT c FROM Conversation c
                WHERE c.parentConversationId = :parentConversationId
                ORDER BY c.createdAt DESC
            """)
    List<Conversation> findByParentConversationId(@Param("parentConversationId") Long parentConversationId);

    @Query("""
                SELECT c.id AS threadConversationId,
                       COUNT(m) AS messageCount,
                       MAX(m.createdAt) AS lastReplyAt,
                       MAX(CASE WHEN m.fromUserId <> :userId THEN m.createdAt ELSE NULL END) AS lastOtherReplyAt,
                       MIN(m.id) AS firstMessageId
                FROM Conversation c
                LEFT JOIN Message m ON m.conversation.id = c.id AND m.isDeleted = false
                WHERE c.id IN :threadConversationIds
                GROUP BY c.id
            """)
    List<ThreadSummaryProjection> findThreadSummariesForThreads(
            @Param("threadConversationIds") List<Long> threadConversationIds,
            @Param("userId") Long userId);

    @Query("SELECT c FROM Conversation c " +
            "LEFT JOIN FETCH c.channel " +
            "WHERE c.channel.id = :channelId")
    Optional<Conversation> findByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT c FROM Conversation c " +
            "LEFT JOIN FETCH c.channel " +
            "WHERE c.id = :id")
    Optional<Conversation> findByIdWithAssociations(@Param("id") Long id);
}
