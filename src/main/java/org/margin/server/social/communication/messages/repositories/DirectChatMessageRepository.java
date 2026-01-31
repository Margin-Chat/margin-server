package org.margin.server.social.communication.messages.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.margin.server.social.communication.messages.models.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DirectChatMessageRepository extends JpaRepository<DirectMessage, Long> {
    @Query("""
            SELECT dm
            FROM DirectMessage dm
            WHERE dm.fromUser.id = :fromUserId
              AND dm.toUser.id = :toUserId
            ORDER BY dm.createdAt ASC
            """)
    List<DirectMessage> findByFromUserIdAndToUserId(@Param("fromUserId") Long fromUserId,
                                                    @Param("toUserId") Long toUserId);

    @Query("""
            SELECT CASE 
                     WHEN dm.fromUser.id = :userId THEN dm.toUser.id 
                     ELSE dm.fromUser.id 
                   END as otherUserId
            FROM DirectMessage dm
            WHERE dm.fromUser.id = :userId OR dm.toUser.id = :userId
            GROUP BY dm.fromUser.id, dm.toUser.id
            ORDER BY MAX(dm.createdAt) DESC
            """)
    List<Long> findRecentChatUserIds(@Param("userId") Long userId);

    @Query("""
            SELECT dm
            FROM DirectMessage dm
            WHERE dm.toUser.id = :userId
            	AND dm.isRead = false
            """)
    List<DirectMessage> findByToUserIdAndWhereIsReadIsFalse(@Param("userId") Long userId);

    @Modifying
    @Query("""
            UPDATE DirectMessage dm
            SET dm.isRead = true
            WHERE dm.fromUser.id = :fromUserId
                AND dm.toUser.id = :toUserId
                AND dm.id IN :messageIds
            """)
    void setMessagesToRead(@Param("fromUserId") Long fromUserId,
                           @Param("toUserId") Long toUserId,
                           @Param("messageIds") List<Long> messageIds);

}
