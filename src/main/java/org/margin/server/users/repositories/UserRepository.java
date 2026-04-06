package org.margin.server.users.repositories;

import org.margin.server.users.models.User;
import org.margin.server.users.repositories.projections.UserWithSharedMarginProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("""
            SELECT u
            FROM User u
            WHERE LOWER(u.handle) = :handle
            """)
    Optional<User> findByHandle(String handle);

    @Query("""
            SELECT new org.margin.server.users.repositories.projections.UserWithSharedMarginProjection(u, m.name)
            FROM User u
            JOIN MarginMember mm ON mm.user = u
            JOIN MarginMember mm2 ON mm2.margin = mm.margin
            JOIN Margin m ON m.id = mm.margin.id
            WHERE mm2.user.id = :searcherId
            AND u.id != :searcherId
            AND LOWER(u.handle) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    List<UserWithSharedMarginProjection> findUsersWithSharedMargins(
            Long searcherId,
            String query
    );

    @Query("""
            SELECT u
            FROM User u
            JOIN MarginMember mb on mb.margin.id = :marginId
            AND mb.user.id = u.id
            WHERE LOWER(u.handle) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    List<User> findUsersByMarginId(Long searcherId, Long marginId, String query);

    Optional<User> findByEmail(String email);
}
