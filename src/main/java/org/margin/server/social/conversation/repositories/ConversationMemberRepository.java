package org.margin.server.social.conversation.repositories;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationMember;
import org.margin.server.social.conversation.models.ConversationMemberId;
import org.margin.server.social.messages.models.dtos.UnreadCountDTO;
import org.margin.server.users.models.User;
import org.margin.server.users.repositories.projections.RecentChatUserProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {
    @Query("SELECT cm.user " +
            "FROM ConversationMember cm " +
            "WHERE cm.conversation.id = :conversationId")
    List<User> findUsersByConversationId(@Param("conversationId") Long conversationId);

    @Query("""
            SELECT
                u,
                m.message,
                m.createdAt,
                CASE WHEN m.fromUser.id != :userId THEN true ELSE false END
                        FROM ConversationMember cm
                        JOIN cm.user u
                        JOIN Message m ON m.conversation.id = cm.conversation.id
                        WHERE cm.conversation.id IN (
                            SELECT cm2.conversation.id
                            FROM ConversationMember cm2
                            WHERE cm2.user.id = :userId
                        )
                        AND u.id != :userId
                        AND cm.conversation.type IN ('DIRECT', 'GROUP')
                        AND m.createdAt = (
                            SELECT MAX(m2.createdAt)
                            FROM Message m2
                            WHERE m2.conversation.id = cm.conversation.id
                        )
                        ORDER BY m.createdAt DESC
            """)
    List<RecentChatUserProjection> findRecentChatUsers(@Param("userId") Long userId);

    @Query("SELECT COUNT(cm) > 0 FROM ConversationMember cm WHERE cm.conversation.id = :conversationId AND cm.user.id = :userId")
    boolean isUserMemberOfConversation(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Query("""
                SELECT 
                    cm.conversation.id,
                    m.fromUser.id,
                    SUM(CASE WHEN cm.conversation.type = 'DIRECT' AND m.createdAt > COALESCE(cm.lastReadAt, cm.joinedAt) AND m.fromUser.id != :userId THEN 1 ELSE 0 END),
                    SUM(CASE WHEN cm.conversation.type = 'CHANNEL' AND m.createdAt > COALESCE(cm.lastReadAt, cm.joinedAt) AND m.fromUser.id != :userId THEN 1 ELSE 0 END)
                FROM ConversationMember cm
                LEFT JOIN Message m ON m.conversation.id = cm.conversation.id
                WHERE cm.user.id = :userId
                GROUP BY cm.conversation.id, m.fromUser.id
                HAVING SUM(CASE WHEN m.createdAt > COALESCE(cm.lastReadAt, cm.joinedAt) AND m.fromUser.id != :userId THEN 1 ELSE 0 END) > 0
            """)
    List<UnreadCountDTO> getUnreadMessagesCounts(@Param("userId") Long userId);

    List<ConversationMember> findByConversation(Conversation conversation);
}
