package org.margin.server.users.repositories;

import org.margin.server.users.models.User;
import org.margin.server.users.repositories.projections.UserWithSharedMarginProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("""
            SELECT u
            FROM User u
            WHERE LOWER(u.username) = :username
            """)
    Optional<User> findByUsername(@Param("username") String username);

    List<User> findAllByIdIn(List<Long> userIds);

    @Query("""
            SELECT u
            FROM User u
            WHERE u.username LIKE :username
            """)
    List<User> findByUsernameLike(@Param("username") String query, Pageable pageable);

    @Query("""
            SELECT new org.margin.server.users.repositories.projections.UserWithSharedMarginProjection(u, m.name)
            FROM User u
            JOIN MarginMember mm ON mm.user = u
            JOIN MarginMember mm2 ON mm2.margin = mm.margin
            JOIN Margin m ON m.id = mm.margin.id
            WHERE mm2.user.id = :searcherId
            AND u.id != :searcherId
            AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    List<UserWithSharedMarginProjection> findUsersInSharedMargins(
            @Param("searcherId") Long searcherId,
            @Param("query") String query
    );

    Optional<User> findByEmail(String email);
}
