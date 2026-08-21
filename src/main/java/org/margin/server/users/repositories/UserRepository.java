package org.margin.server.users.repositories;

import org.margin.server.users.models.User;
import org.margin.server.users.repositories.projections.UserWithSharedMarginProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("""
            SELECT new org.margin.server.users.repositories.projections.UserWithSharedMarginProjection(u, m.name)
            FROM User u
            JOIN MarginMember mm ON mm.userId = u.id
            JOIN MarginMember mm2 ON mm2.margin = mm.margin
            JOIN Margin m ON m.id = mm.margin.id
            WHERE mm2.userId = :searcherId
            AND u.id != :searcherId
            AND LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    List<UserWithSharedMarginProjection> findUsersWithSharedMargins(
            Long searcherId,
            String query
    );

    @Query("""
            SELECT u
            FROM User u
            JOIN MarginMember mb on mb.margin.id = :marginId
            AND mb.userId = u.id
            WHERE LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    List<User> findUsersByMarginId(Long searcherId, Long marginId, String query);

    Optional<User> findByEmail(String email);

    @Query("""
            SELECT u FROM User u
            WHERE u.accountType = org.margin.server.users.models.UserAccountType.GUEST
              AND u.guestExpiresAt IS NOT NULL
              AND u.guestExpiresAt < :now
            ORDER BY u.guestExpiresAt
            """)
    List<User> findExpiredGuests(@Param("now") Instant now, Pageable pageable);
}
