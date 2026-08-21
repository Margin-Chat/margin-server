package org.margin.server.social.margin.repositories;

import org.margin.server.social.margin.entities.MarginMember;
import org.margin.server.social.margin.models.MarginRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MarginMemberRepository extends JpaRepository<MarginMember, Long> {

    Optional<MarginMember> findByMargin_IdAndRole(Long marginId, MarginRole role);

    @Query("""
            SELECT mm
            FROM MarginMember mm
            WHERE mm.userId = :userId
            """)
    List<MarginMember> findMarginMembersByUser(Long userId);

    Optional<MarginMember> findByUserIdAndMarginId(Long userId, Long marginId);

    boolean existsByUserIdAndMarginId(Long userId, Long marginId);

    boolean existsByUserId(Long userId);

    @Query("""
            SELECT CASE WHEN COUNT(mm) > 0 THEN true ELSE false END
            FROM MarginMember mm
            WHERE mm.margin.id = :marginId
            AND mm.userId = :userId
            """)
    boolean existsByMarginIdAndUserId(Long marginId, Long userId);

    List<MarginMember> findByMargin_Id(Long marginId);
}
