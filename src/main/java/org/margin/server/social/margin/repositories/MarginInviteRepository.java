package org.margin.server.social.margin.repositories;

import org.margin.server.social.margin.entities.MarginInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MarginInviteRepository extends JpaRepository<MarginInvite, Long> {

    @Query("SELECT i FROM MarginInvite i WHERE i.inviteCode = :code")
    Optional<MarginInvite> findByInviteCode(@Param("code") String inviteCode);

    @Query("""
            SELECT i FROM MarginInvite i
            WHERE i.invitedUserId = :userId
            AND i.status = :status
            AND i.expiresAt > :now
            """)
    List<MarginInvite> findInvitesForUserByStatus(
            @Param("userId") Long userId,
            @Param("status") MarginInvite.InviteStatus status,
            @Param("now") Instant now);

    @Query("""
            SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END
            FROM MarginInvite i
            WHERE i.margin.id = :marginId
            AND i.invitedUserId = :userId
            AND i.status = :status
            """)
    boolean existsPendingInvite(
            @Param("marginId") Long marginId,
            @Param("userId") Long userId,
            @Param("status") MarginInvite.InviteStatus status);
}