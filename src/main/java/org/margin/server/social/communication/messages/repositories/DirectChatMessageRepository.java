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
            WHERE dm.fromUserId = :fromUserId
              AND dm.toUserId = :toUserId
            ORDER BY dm.createdAt ASC
            """)
    List<DirectMessage> findByFromUserIdAndToUserId(@Param("fromUserId") Long fromUserId,
                                                    @Param("toUserId") Long toUserId);

    @Query("""
            SELECT CASE 
                     WHEN dm.fromUserId = :userId THEN dm.toUserId 
                     ELSE dm.fromUserId 
                   END as otherUserId
            FROM DirectMessage dm
            WHERE dm.fromUserId = :userId OR dm.toUserId = :userId
            GROUP BY dm.fromUserId, dm.toUserId
            ORDER BY MAX(dm.createdAt) DESC
            """)
    List<Long> findRecentChatUserIds(@Param("userId") Long userId);

    @Query("""
            SELECT dm
            FROM DirectMessage dm
            WHERE dm.toUserId = :userId
            	AND dm.isRead = false
            """)
    List<DirectMessage> findByToUserIdAndWhereIsReadIsFalse(@Param("userId") Long userId);

    @Modifying
    @Query("""
            UPDATE DirectMessage dm
            SET dm.isRead = true
            WHERE dm.fromUserId = :fromUserId
                AND dm.toUserId = :toUserId
                AND dm.id IN :messageIds
            """)
    void setMessagesToRead(@Param("fromUserId") Long fromUserId,
                           @Param("toUserId") Long toUserId,
                           @Param("messageIds") List<Long> messageIds);

}
