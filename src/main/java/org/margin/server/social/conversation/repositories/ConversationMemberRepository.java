package org.margin.server.social.conversation.repositories;

import org.margin.server.social.conversation.models.Conversation;
import org.margin.server.social.conversation.models.ConversationInviteStatus;
import org.margin.server.social.conversation.models.ConversationMember;
import org.margin.server.social.conversation.models.ConversationMemberId;
import org.margin.server.social.conversation.models.projections.UnreadConversationProjection;
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
                cm.conversation.id,
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
            SELECT cm.conversation.id AS conversationId,
                   cm.conversation.type AS type,
                   mg.id AS marginId
            FROM ConversationMember cm
            LEFT JOIN cm.conversation.channel ch
            LEFT JOIN ch.space sp
            LEFT JOIN sp.margin mg
            WHERE cm.user.id = :userId
              AND EXISTS (
                SELECT 1 FROM Message m
                WHERE m.conversation.id = cm.conversation.id
                  AND m.fromUser.id != :userId
                  AND m.createdAt > COALESCE(cm.lastReadAt, cm.joinedAt)
              )
            """)
    List<UnreadConversationProjection> getUnreadConversations(@Param("userId") Long userId);

    List<ConversationMember> findByConversation(Conversation conversation);

    @Query("SELECT cm FROM ConversationMember cm JOIN FETCH cm.conversation JOIN FETCH cm.user WHERE cm.user.id = :userId AND cm.inviteStatus = :status")
    List<ConversationMember> findByUserIdAndInviteStatus(@Param("userId") Long userId,
                                                         @Param("status") ConversationInviteStatus status);

    @Query("SELECT cm FROM ConversationMember cm WHERE cm.conversation.id = :conversationId AND cm.user.id = :userId")
    java.util.Optional<ConversationMember> findByConversationIdAndUserId(@Param("conversationId") Long conversationId,
                                                                          @Param("userId") Long userId);
}
